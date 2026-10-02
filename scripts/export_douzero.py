#!/usr/bin/env python3
"""Rebuild the bundled offline DouZero-WP models and Python parity fixtures.

Build-time dependencies (never needed by the game):
  torch==2.8.0 onnx==1.19.1 onnxruntime==1.23.2

Downloads are pinned by revision and SHA-256. PyTorch loads weights_only=True;
the downloaded Python reference implementation is also hash checked before use.
"""
from __future__ import annotations

import argparse
import ast
from collections import Counter
import hashlib
import json
from pathlib import Path
from types import SimpleNamespace
import urllib.request

ROOT = Path(__file__).resolve().parents[1]
SOURCE_REV = "718a5c920bf3361e34178a38f3b80458e176b351"
MIRROR_REV = "57b3914046c2a0877016b8b8830fd07cf5b0ba08"
SOURCE = f"https://raw.githubusercontent.com/kwai/DouZero/{SOURCE_REV}"
MIRROR = f"https://huggingface.co/palemoky/douzero-baselines/resolve/{MIRROR_REV}"
REFERENCE_HASHES = {
    "douzero/dmc/models.py": "3ad5483bf3d82b3a4174ee3a8893891dafcd635590e9cd7372fabe5988e8a190",
    "douzero/env/env.py": "43012a3b7672e054b81212dc5b2456b7648ad1d46ebb1217363d897f74233330",
    "LICENSE": "c71d239df91726fc519c6eb72d318ec65820627232b2f796219e87dcf35d0ab4",
}
CHECKPOINT_HASHES = {
    "landlord": "132e26479fcb69f457ddbf1ad32a7bdc3aa73cde80a28cc91f29a62c6075955a",
    "landlord_up": "b849ea518e66f088d635d3a29956da880f7b5ca89fabc5f70fc356c106ca6a15",
    "landlord_down": "964720faf583f4905662c94aaf73b1a97352b76ae3686fcc949beabbacdd95d1",
}


def checked_download(url: str, destination: Path, expected: str) -> bytes:
    if not destination.exists():
        destination.parent.mkdir(parents=True, exist_ok=True)
        request = urllib.request.Request(url, headers={"User-Agent": "laozhang-doudizhu-model-export/1"})
        with urllib.request.urlopen(request, timeout=120) as response:
            data = response.read()
        if hashlib.sha256(data).hexdigest() != expected:
            raise ValueError(f"SHA-256 mismatch for {url}")
        destination.write_bytes(data)
    data = destination.read_bytes()
    if hashlib.sha256(data).hexdigest() != expected:
        raise ValueError(f"SHA-256 mismatch for cached file {destination}")
    return data


def load_reference(cache: Path):
    models_source = checked_download(
        f"{SOURCE}/douzero/dmc/models.py", cache / "models.py", REFERENCE_HASHES["douzero/dmc/models.py"]
    )
    env_source = checked_download(
        f"{SOURCE}/douzero/env/env.py", cache / "env.py", REFERENCE_HASHES["douzero/env/env.py"]
    )
    model_namespace = {}
    exec(compile(models_source, "DouZero/models.py", "exec"), model_namespace)
    # get_obs and its helpers are unchanged. Only remove GameEnv's import: the
    # reference encoder can run on a public infoset without the upstream game.
    tree = ast.parse(env_source)
    tree.body = [node for node in tree.body if not (
        isinstance(node, ast.ImportFrom) and node.module == "douzero.env.game"
    )]
    env_namespace = {}
    exec(compile(tree, "DouZero/env.py", "exec"), env_namespace)
    return model_namespace["model_dict"], env_namespace["get_obs"]


def physical_mask(cards):
    return sum(1 << bit for bit in cards)


def douzero_rank(bit):
    rank = bit // 4
    return rank + 3 if rank < 12 else (17, 20, 30)[rank - 12]


def ranks(cards):
    return sorted(douzero_rank(bit) for bit in cards)


def counts(cards):
    counter = Counter(bit // 4 for bit in cards)
    return [counter[rank] for rank in range(15)]


def fixture_observations(get_obs):
    """A conserved physical deal, with bomb, pass, free lead and long histories."""
    landlord = 1
    seats = {"landlord": landlord, "landlord_down": 2, "landlord_up": 0}
    rest = sorted(list(range(4, 52)) + [52, 56], key=lambda bit: (bit * 37 + 11) % 59)
    hands = {1: list(range(4)) + rest[:16], 2: rest[16:33], 0: rest[33:]}
    bottom = hands[landlord][-3:]
    played = {0: [], 1: [], 2: []}
    log = []
    bombs = 0
    trick_key = None
    trick_owner = -1
    consecutive_passes = 0
    snapshots = []
    snapshot_steps = {0, 1, 2, 3, 4, 5, 18, 19, 20}

    def candidates(seat):
        by_rank = {}
        for bit in sorted(hands[seat]):
            by_rank.setdefault(bit // 4, []).append(bit)
        moves = []
        if trick_key is None:
            for width in (1, 2, 3):
                moves.extend(cards[:width] for rank, cards in by_rank.items()
                             if len(cards) >= width and (width == 1 or rank < 13))
        elif trick_key >> 8 == 0:
            moves.extend(cards[:1] for rank, cards in by_rank.items() if rank > (trick_key >> 4) % 16)
        for rank, cards in by_rank.items():
            if len(cards) == 4 and (trick_key is None or trick_key >> 8 != 12 or rank > (trick_key >> 4) % 16):
                moves.append(cards[:])
        if 13 in by_rank and 14 in by_rank:
            moves.append([52, 56])
        if trick_key is not None:
            moves.append([])
        return moves

    for step in range(24):
        seat = (landlord + step) % 3
        position = next(role for role, role_seat in seats.items() if role_seat == seat)
        actions = candidates(seat)
        if not actions or not hands[seat]:
            raise ValueError("The scripted parity deal ended too early")
        if step in snapshot_steps:
            last_action = log[-1]["physical"] if log and log[-1]["physical"] else (log[-2]["physical"] if len(log) > 1 else [])
            latest_by_role = {}
            for role, role_seat in seats.items():
                previous = next((item for item in reversed(log) if item["seat"] == role_seat), None)
                latest_by_role[role] = ranks(previous["physical"]) if previous else []
            infoset = SimpleNamespace(
                player_position=position,
                legal_actions=[ranks(action) for action in actions],
                player_hand_cards=ranks(hands[seat]),
                other_hand_cards=ranks([bit for role_seat in seats.values() if role_seat != seat for bit in hands[role_seat]]),
                last_move=ranks(last_action),
                played_cards={role: ranks(played[role_seat]) for role, role_seat in seats.items()},
                last_move_dict=latest_by_role,
                num_cards_left_dict={role: len(hands[role_seat]) for role, role_seat in seats.items()},
                bomb_num=bombs,
                card_play_action_seq=[ranks(item["physical"]) for item in log],
            )
            encoded = get_obs(infoset)
            snapshots.append({
                "name": f"{position}_step_{step}", "position": position,
                "seat": seat, "landlord": landlord, "handBits": physical_mask(hands[seat]),
                "bottomBits": physical_mask(bottom), "handSizes": [len(hands[index]) for index in range(3)],
                "playedByBits": [physical_mask(played[index]) for index in range(3)],
                "bombs": bombs, "trickOwner": trick_owner, "trickKey": trick_key,
                "log": [{"seat": item["seat"], "cardsBits": physical_mask(item["physical"]), "comboKey": item["comboKey"]} for item in log],
                "x": encoded["x_no_action"].tolist(), "z": encoded["z"].reshape(-1).tolist(),
                "actions": [counts(action) for action in actions],
                "_x_batch": encoded["x_batch"], "_z": encoded["z"],
            })
        if step == 0:
            action, key = list(range(4)), (12 << 8) | 1
        elif step in (1, 2) or (step % 7 == 0 and trick_key is not None):
            action, key = [], None
        else:
            # Continue this fixture with single cards only after the first bomb.
            single = next((move for move in actions if len(move) == 1), None)
            if single is None:
                action, key = [], None
            else:
                action, key = single, ((single[0] // 4) << 4) | 1
        if not action:
            consecutive_passes += 1
            if consecutive_passes == 2:
                trick_key, trick_owner, consecutive_passes = None, -1, 0
        else:
            consecutive_passes = 0
            trick_key, trick_owner = key, seat
            bombs += int(key >> 8 in (12, 13))
            for bit in action:
                hands[seat].remove(bit)
            played[seat].extend(action)
        log.append({"seat": seat, "physical": action, "comboKey": key})
    return snapshots


def main():
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("--cache-dir", type=Path, default=Path("/tmp/douzero-export-cache"))
    parser.add_argument("--output-dir", type=Path, default=ROOT / "engine/src/main/resources/douzero")
    parser.add_argument("--fixture-dir", type=Path, default=ROOT / "engine/src/test/resources/douzero")
    args = parser.parse_args()
    import numpy as np
    import onnx
    import onnxruntime as ort
    import torch

    torch.set_num_threads(1)
    model_dict, get_obs = load_reference(args.cache_dir)
    args.output_dir.mkdir(parents=True, exist_ok=True)
    args.fixture_dir.mkdir(parents=True, exist_ok=True)
    checked_download(f"{SOURCE}/LICENSE", args.output_dir / "LICENSE", REFERENCE_HASHES["LICENSE"])

    class SharedHistoryModel(torch.nn.Module):
        def __init__(self, model):
            super().__init__()
            self.model = model

        def forward(self, z, x):
            history, _ = self.model.lstm(z)
            common = history[:, -1, :].expand(x.shape[0], -1)
            value = torch.cat((common, x), dim=-1)
            for layer in (self.model.dense1, self.model.dense2, self.model.dense3,
                          self.model.dense4, self.model.dense5):
                value = torch.relu(layer(value))
            return self.model.dense6(value)

    cases = fixture_observations(get_obs)
    manifest = {"sourceRevision": SOURCE_REV, "mirrorRevision": MIRROR_REV, "objective": "WP",
                "opset": 17, "exportVersions": {"torch": torch.__version__, "onnx": onnx.__version__, "onnxruntime": ort.__version__},
                "models": {}}
    for role, expected_hash in CHECKPOINT_HASHES.items():
        checkpoint = args.cache_dir / f"{role}.ckpt"
        url = f"{MIRROR}/checkpoints/douzero_WP/{role}.ckpt"
        checked_download(url, checkpoint, expected_hash)
        model = model_dict[role]().cpu().eval()
        model.load_state_dict(torch.load(checkpoint, map_location="cpu", weights_only=True))
        wrapped = SharedHistoryModel(model).eval()
        x_size = 373 if role == "landlord" else 484
        output = args.output_dir / f"{role}.onnx"
        torch.onnx.export(
            wrapped, (torch.zeros(1, 5, 162), torch.zeros(3, x_size)), str(output),
            input_names=["z", "x"], output_names=["values"],
            dynamic_axes={"x": {0: "actions"}, "values": {0: "actions"}},
            opset_version=17, dynamo=False,
        )
        graph = onnx.load(output)
        onnx.helper.set_model_props(graph, {
            "source": "kwai/DouZero", "source_revision": SOURCE_REV,
            "checkpoint_mirror": "palemoky/douzero-baselines", "mirror_revision": MIRROR_REV,
            "checkpoint_sha256": expected_hash, "license": "Apache-2.0", "role": role,
            "objective": "WP", "history": "last 15 actions, left padded, chronological, [1,5,162]",
        })
        onnx.checker.check_model(graph)
        onnx.save(graph, output)
        options = ort.SessionOptions()
        options.intra_op_num_threads = 1
        session = ort.InferenceSession(str(output), options, providers=["CPUExecutionProvider"])
        largest_error = 0.0
        for case in cases:
            if case["position"] != role:
                continue
            x, z = case["_x_batch"], case["_z"][None, :, :].astype(np.float32)
            with torch.inference_mode():
                canonical = model(torch.from_numpy(np.repeat(z, len(x), axis=0)), torch.from_numpy(x), return_value=True)["values"].numpy()
            predicted = session.run(["values"], {"z": z, "x": x})[0]
            np.testing.assert_allclose(predicted, canonical, rtol=1e-4, atol=1e-5)
            # Exercise dynamic batch sizes, including a single legal action.
            for count in (1, min(3, len(x))):
                reduced = session.run(["values"], {"z": z, "x": x[:count]})[0]
                np.testing.assert_allclose(reduced, canonical[:count], rtol=1e-4, atol=1e-5)
            largest_error = max(largest_error, float(np.max(np.abs(predicted - canonical))))
            case["scores"] = canonical.reshape(-1).tolist()
        manifest["models"][role] = {
            "checkpointUrl": url, "checkpointSha256": expected_hash,
            "onnxSha256": hashlib.sha256(output.read_bytes()).hexdigest(),
            "bytes": output.stat().st_size, "maxParityAbsoluteError": largest_error,
        }
        print(f"{role}: {output.stat().st_size:,} bytes, max PyTorch/ONNX error {largest_error:.3g}")
    for case in cases:
        del case["_x_batch"], case["_z"]
    (args.fixture_dir / "parity.json").write_text(
        json.dumps({"sourceRevision": SOURCE_REV, "checkpointHashes": CHECKPOINT_HASHES, "cases": cases}, indent=2) + "\n"
    )
    (args.output_dir / "manifest.json").write_text(json.dumps(manifest, indent=2) + "\n")


if __name__ == "__main__":
    main()
