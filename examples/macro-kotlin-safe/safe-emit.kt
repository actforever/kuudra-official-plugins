macro {
    whenCondition(ref("event#hello-world.message").eq("hello-world"), {
        emit("macro.kotlin.completed", mapOf("macro" to mapOf("result" to "compiled-and-executed")))
    }).otherwise {
        emit("macro.kotlin.skipped", mapOf("macro" to mapOf("result" to "unexpected-input")))
    }
}
