package com.kfu.imim.networking

import com.kfu.imim.utils.Field
import com.kfu.imim.utils.LobbyInfo
import com.kfu.imim.utils.Results
import com.kfu.imim.utils.StartGameInfo
import kotlinx.serialization.decodeFromString
import kotlinx.serialization.json.Json

class Game(private val startGameInfo: StartGameInfo, lobbyInfo: LobbyInfo) {
    private var isEnded = false
    private var field = Field(
        startGameInfo.width,
        startGameInfo.height,
        startGameInfo.position,
        startGameInfo.opponentPosition,
        startGameInfo.barriers
    )
    private var obstaclesCount = lobbyInfo.playerBarrierCount
    private val send = mutableListOf<(String)->Unit>()
    private val receive = mutableListOf<(Unit)->String>()
    private var result = ""
    
    fun play() : String {
        //TODO()
        if (startGameInfo.move) {
            makeMove()
        } 
        while (true) {
            waitMoveOrEndGame()
            if (!isEnded) {
                makeMove()
            } else break
        }
        return result
    }

    private fun waitMoveOrEndGame() {
        receive.forEach {
            val str = it.invoke(Unit)
            if (str.startsWith("SOCKET STEP")) {
                field = Json.decodeFromString(str.removePrefix("SOCKET STEP"))
            } else {
                isEnded = true
                val results = Json.decodeFromString<Results>(str.removePrefix("SOCKET ENDGAME"))
                result = results.result
            }
        }
    }

    private fun makeMove() {
        val moves = expandPlayer()
        val obstacles = if (obstaclesCount > 0) {
            expandObstacles()
        } else null
        val turn = chooseBestTurn(moves, obstacles)
        send.forEach { it.invoke(turn) }
    }

    private fun chooseBestTurn(moves: List<Array<Int>>?, obstacles: Set<Array<Array<Int>>>?): String {
        //TODO("Not yet implemented")
        return ""
    }

    private fun expandObstacles() : Set<Array<Array<Int>>>{
        //TODO("Not yet implemented")
        return emptySet()
    }

    //Возвращаем список клеток, куда мы можем сходить или null, если некуда
    private fun expandPlayer() : List<Array<Int>>?{
        //Потенциально можем сходить в 4 клетки, вверх, вниз, влево, вправо
        var res = listOf(
            arrayOf(field.position[0] - 1, field.position[1]),
            arrayOf(field.position[0] + 1, field.position[1]),
            arrayOf(field.position[0], field.position[1] - 1),
            arrayOf(field.position[0], field.position[1] + 1))
        //Отсеиваем (filter) те ходы из 4, которые нельзя сделать
        res = res.filter { canPlayerMove(it[0], it[1]) }
        //Возвращаем те, что остались или null
        return if (res.isEmpty()) null else res
    }

    //Проверяем, находится ли точка (х, у) на поле
    private fun isInBoard(x: Int, y: Int) : Boolean {
        return x in 0 until field.height && y in 0 until field.width
    }

    //Проверяем, можно ли нам сходить из текущей позиции (position[0], position[1]) в (toX, toY)
    private fun canPlayerMove(toX: Int, toY: Int) : Boolean {
        //Если конечная точка вне доски - нельзя
        if (!isInBoard(toX, toY)) return false
        //Запрыгнуть на оппонента тоже нельзя
        if (toX == field.opponentPosition[0] && toY == field.opponentPosition[1]) return false
        //Если пересекаем препятствие - нельзя
        if (!canMove(field.position[0], field.position[1], toX, toY)) return false
        return true
    }

    private fun canMove(fromX: Int, fromY: Int, toX: Int, toY: Int): Boolean {
        return !field.barriers.any {
            it[0][0] == fromX && it[0][1] == fromY && it[1][0] == toX && it[1][1] == toY ||
                    it[0][0] == toX && it[0][1] == toY && it[1][0] == fromX && it[1][1] == fromY ||
                    it[2][0] == fromX && it[2][1] == fromY && it[3][0] == toX && it[3][1] == toY ||
                    it[2][0] == toX && it[2][1] == toY && it[3][0] == fromX && it[3][1] == fromY
        }
    }

    fun addSendListener(sendFunction: (String)->Unit) {
        send.add(sendFunction)
    }
}