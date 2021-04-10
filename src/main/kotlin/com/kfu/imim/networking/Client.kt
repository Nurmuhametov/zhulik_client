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
    private val channel = Channel<String>(1)

    fun start() {
        communicator.addDataReceivedListener(::dataReceived)
        communicator.start()
        login()
        repeat(gamesCount) {
            playGame()
        }
    }

    private fun playGame() {
        //TODO("Not yet implemented")
        val lobbyInfo = joinLobby()
        val startGameInfo = waitForStart()
        val game = Game(startGameInfo, lobbyInfo)
        game.addSendListener(::sendData)
        val res = game.play()
        println(res)
    }

    private fun sendData(data: String) {
        communicator.sendData(data)
    }

    private fun waitForStart() : StartGameInfo{
        return runBlocking {
            val data = channel.receive()
            Json.decodeFromString(data)
        }
    }

    private fun joinLobby() : LobbyInfo{
        val lobbyID = LobbyID(null)
        communicator.sendData("SOCKET JOINLOBBY " + Json.encodeToString(lobbyID) + "\n")
        val response : String
        runBlocking {
            response = channel.receive()
        }
        val joinLobbyResponse = Json.decodeFromString<JoinLobbyResponse>(response)
        assert(joinLobbyResponse.SUCCESS)
        return joinLobbyResponse.DATA
    }

    private fun login() {
        val loginInfo = LoginInfo("nastenko")
        communicator.sendData("CONNECTION "+ Json.encodeToString(loginInfo) + "\n")
        val response : String
        runBlocking {
            response = channel.receive()
        }
        val msg = Json.decodeFromString<Message>(response)
        assert(msg.MESSAGE == "LOGIN OK")
    }

    private fun dataReceived(data: String) {
        //TODO()
    }
}