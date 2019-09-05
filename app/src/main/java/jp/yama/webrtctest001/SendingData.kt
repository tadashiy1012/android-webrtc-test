package jp.yama.webrtctest001

data class SendingData(
    val to: String = "default@890",
    val type: String = "consume",
    var sdp: String,
    var uuid: String,
    val key: String = "default") {
}