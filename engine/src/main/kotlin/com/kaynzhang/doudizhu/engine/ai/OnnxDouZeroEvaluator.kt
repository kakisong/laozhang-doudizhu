package com.kaynzhang.doudizhu.engine.ai

import ai.onnxruntime.OnnxTensor
import ai.onnxruntime.OrtEnvironment
import ai.onnxruntime.OrtSession
import com.kaynzhang.doudizhu.engine.model.Counts
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive
import java.nio.FloatBuffer

/**
 * CPU inference for the three bundled DouZero WP networks. Models are loaded lazily and shared
 * by all tables. Each call owns and closes its tensors/results; OrtSession supports concurrent
 * runs, so returning to a table after cancelling an earlier decision cannot reuse its buffers.
 * The exported graph encodes the common history once and scores every legal action in a batch.
 */
internal object OnnxDouZeroEvaluator : DouZeroEvaluator {
    private val sessions = mutableMapOf<DouZeroPosition, OrtSession>()
    private val environment by lazy { OrtEnvironment.getEnvironment().apply { setTelemetry(false) } }

    private fun session(position: DouZeroPosition): OrtSession = synchronized(sessions) {
        sessions.getOrPut(position) {
            val path = "/douzero/${position.resourceName}.onnx"
            val bytes = requireNotNull(javaClass.getResourceAsStream(path)) {
                "Missing bundled DouZero model: $path"
            }.use { it.readBytes() }
            OrtSession.SessionOptions().use { options ->
                // One inference thread avoids competing with rendering or the other bot's turn.
                options.setIntraOpNumThreads(1)
                options.setInterOpNumThreads(1)
                options.setOptimizationLevel(OrtSession.SessionOptions.OptLevel.ALL_OPT)
                environment.createSession(bytes, options)
            }
        }
    }

    override suspend fun scores(features: DouZeroInput, actions: List<Counts>): FloatArray {
        val context = currentCoroutineContext()
        context.ensureActive()
        require(actions.isNotEmpty())
        val width = features.position.xSize
        require(features.x.size == width - CARDS && features.z.size == HISTORY_SIZE)
        val session = session(features.position)
        context.ensureActive()
        val scores = FloatArray(actions.size)
        OnnxTensor.createTensor(environment, FloatBuffer.wrap(features.z), longArrayOf(1, 5, 162)).use { z ->
            // Bound scratch space even for hands with thousands of wing combinations, and allow
            // a cancelled/paused game to stop between batches without waiting for every move.
            for (start in actions.indices step BATCH_SIZE) {
                context.ensureActive()
                val size = minOf(BATCH_SIZE, actions.size - start)
                val batch = FloatArray(size * width)
                for (i in 0 until size) {
                    val offset = i * width
                    features.x.copyInto(batch, offset)
                    DouZeroFeatures.writeCards(actions[start + i], batch, offset + features.x.size)
                }
                OnnxTensor.createTensor(environment, FloatBuffer.wrap(batch), longArrayOf(size.toLong(), width.toLong())).use { x ->
                    session.run(mapOf("z" to z, "x" to x)).use { result ->
                        val output = result[0] as OnnxTensor
                        require(output.info.shape.contentEquals(longArrayOf(size.toLong(), 1))) {
                            "Unexpected DouZero score shape"
                        }
                        output.floatBuffer.get(scores, start, size)
                    }
                }
            }
        }
        context.ensureActive()
        return scores
    }

    private const val CARDS = 54
    private const val HISTORY_SIZE = 5 * 162
    private const val BATCH_SIZE = 128
}
