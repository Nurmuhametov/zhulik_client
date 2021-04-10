import com.kfu.imim.networking.Client

fun main(argv: Array<String>) {
    val client = Client(argv[1].toInt(), argv[2], argv[3].toInt())
    client.start()
}