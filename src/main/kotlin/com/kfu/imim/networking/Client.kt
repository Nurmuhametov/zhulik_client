package com.kfu.imim.networking

import com.kfu.imim.utils.*
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.runBlocking
import kotlinx.serialization.decodeFromString
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import java.net.Socket

class Client(private val gamesCount: Int, serverAddress: String, serverPort: Int) {
    private val communicator = Communicator(Socket(serverAddress, serverPort))
    private val commonChannel = Channel<String>(capacity = Channel.UNLIMITED)
    private val stepsChannel = Channel<String>(1)
    private val joinChannel = Channel<String>(1)
    private val startChannel = Channel<String>(1)

    fun start() {
        communicator.addDataReceivedListener(::dataReceived)
        communicator.start()
        println("Login: start")
        login()
        repeat(gamesCount) {
            playGame()
        }
        communicator.sendData("DISCONNECT {\"QUIT\":\"\"}")
    }

    private fun playGame() {
        val lobbyInfo = joinLobby()
        val startGameInfo = waitForStart()
        val game = Game(startGameInfo, lobbyInfo)
        game.addSendListener(::sendData)
        game.addReceiveListener(::receiveStep)
        val res = game.play()
        println(res)
        println("Play game: success")
    }

    private fun sendData(data: String) {
        communicator.sendData(data)
    }

    private fun receiveStep() : String {
        val str: String
        runBlocking {
            str = stepsChannel.receive()
        }
        return str
    }

    private fun waitForStart() : StartGameInfo{
        println("Play game: waiting")
        return runBlocking {
            val data = startChannel.receive()
            Json.decodeFromString(data.removePrefix("SOCKET STARTGAME"))
        }
    }

    private fun joinLobby() : LobbyInfo{
        val lobbyID = LobbyID(null)
        communicator.sendData("SOCKET JOINLOBBY " + Json.encodeToString(lobbyID) + "\n")
        val response : String
        runBlocking {
            response = joinChannel.receive()
        }
        val joinLobbyResponse = Json.decodeFromString<JoinLobbyResponse>(response)
        assert(joinLobbyResponse.SUCCESS)
        println("Join lobby: success")
        return joinLobbyResponse.DATA
    }

    private fun login() {
        val loginInfo = LoginInfo("nastenko")
        communicator.sendData("CONNECTION "+ Json.encodeToString(loginInfo) + "\n")
        val response : String
        runBlocking {
            response = commonChannel.receive()
        }
        val msg = Json.decodeFromString<Message>(response)
        assert(msg.MESSAGE == "LOGIN OK")
        println("Login: success")
    }

    private fun dataReceived(data: String) {
        //TODO("Раскидать по каналам")
        if (data.contains(Regex("(\\{\"DATA\":.*})"))){
            runBlocking {
                joinChannel.send(data)
            }
        } else if (data.startsWith("SOCKET STEP") || data.startsWith("SOCKET ENDGAME")){
            runBlocking {
                stepsChannel.send(data)
            }
        } else if (data.startsWith("SOCKET STARTGAME")){
            runBlocking {
                startChannel.send(data)
            }
        } else {
            runBlocking {
                commonChannel.send(data)
            }
        }
    }
}