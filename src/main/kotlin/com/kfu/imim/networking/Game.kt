package com.kfu.imim.networking

import com.kfu.imim.utils.Field
import com.kfu.imim.utils.LobbyInfo
import com.kfu.imim.utils.Results
import com.kfu.imim.utils.StartGameInfo
import kotlinx.serialization.decodeFromString
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import java.util.*
import java.util.logging.Level
import java.util.logging.LogRecord
import java.util.logging.Logger
import kotlin.reflect.KFunction0

class Game(private val startGameInfo: StartGameInfo, lobbyInfo: LobbyInfo) {
    private var isEnded = false
    private var field = Field(
        startGameInfo.width,
        startGameInfo.height,
        startGameInfo.position,
        startGameInfo.opponentPosition,
        startGameInfo.barriers
    )
    private val goal = if (startGameInfo.position[0] == 0) startGameInfo.height - 1 else 0
    private var obstaclesCount = lobbyInfo.playerBarrierCount
    private val send = mutableListOf<(String)->Unit>()
    private val receive = mutableListOf<KFunction0<String>>()
    private var result = ""
    
    fun play() : String {
        if (startGameInfo.move) {
            println("I'm first!")
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
        println("Play game: waiting turn")
        receive.forEach {
            val str = it.invoke()
            if (str.startsWith("SOCKET STEP")) {
                field = Json.decodeFromString(str.removePrefix("SOCKET STEP"))
            } else {
                isEnded = true
                val results = Json.decodeFromString<Results>(str.removePrefix("SOCKET ENDGAME"))
                result = results.result
            }
        }
        println("Play game: received turn")
    }

    private fun makeMove() {
//        println("Play game: calculating turn")
        val moves = expandMoves()
//        println("Expand player ended")
        val obstacles = if (obstaclesCount > 0) {
            expandObstacles(field.barriers)
        } else null
        val turn = chooseBestTurn(moves, obstacles)
        send.forEach { it.invoke(turn) }
        println("Play game: turn sent")
    }

    private fun chooseBestTurn(moves: List<List<Int>>?, obstacles: Set<Array<Array<Int>>>?): String {
        if (moves != null && obstacles != null) {
//            println("Считаю кратчайший путь для себя")
            val myWay = findShortestWay(field.position, goal, field.barriers, field.opponentPosition, false)
//            println("Считаю кратчайший путь для врага")
            val opponentsWay = findShortestWay(field.opponentPosition, field.height - 1 - goal, field.barriers, field.position, true)
            if (myWay.size < opponentsWay.size) {
//                val move = moves.filter { myWay.contains(it) }
//                assert(move.isNotEmpty())
                field = Field(field.width, field.height, myWay[1], field.opponentPosition, field.barriers)
            } else {
//                println("Длина пути врага ${opponentsWay.size}, ищу препятствие")
                val obstacle = findWorstObstacle(obstacles)
                field.barriers.add(obstacle)
                obstaclesCount -= 1
            }
        } else if (obstacles == null && moves != null) {
            val myWay = findShortestWay(field.position, goal, field.barriers, field.opponentPosition, false)
//            val move = moves.filter { myWay.contains(it) }
//            assert(move.isNotEmpty())
            field = Field(field.width, field.height, myWay[1], field.opponentPosition, field.barriers)
        } else if (obstacles != null && moves == null) {
            val obstacle = findWorstObstacle(obstacles)
            field.barriers.add(obstacle)
            obstaclesCount -= 1
        }
        println("Choose turn ended")
        return "SOCKET STEP " + Json.encodeToString(field)
    }

    private fun findWorstObstacle(obstacles: Set<Array<Array<Int>>>): Array<Array<Int>> {
        var res = obstacles.first()
        var length = 0
//        println("У меня ${obstacles.size} вариантов препятствий")
        for (i in obstacles) {
            val newSetBarriers = mutableSetOf<Array<Array<Int>>>()
            newSetBarriers.addAll(field.barriers)
            newSetBarriers.add(i)
            val newLength = findShortestWay(field.opponentPosition, field.height - 1 - goal, newSetBarriers, field.position, true).size
            if (newLength > length) {
                res = i
                length = newLength
            }
        }
//        println("Теперь длина пути врага $length")
        return res
    }

    private fun findShortestWay(position: List<Int>, goal: Int, obstacles: Set<Array<Array<Int>>>, opponent: List<Int>, canStepOnOpponent: Boolean): List<List<Int>> {
        val result = mutableListOf<List<Int>>()
        var d = 0
        val visitedCells = mutableListOf(position)
        val distances = mutableMapOf(Pair(position, d))
        var notFound = true
        while (notFound) {
            val positions = distances.keys.filter { distances[it] == d }
            for (p in positions) {
                expandPlayer(p, obstacles, opponent, canStepOnOpponent)?.forEach {
                    if (it[0] == goal && notFound) {
                        visitedCells.add(it)
                        distances[it] = d + 1
                        notFound = false
                    }
                    else {
                        if (notFound && !visitedCells.contains(it)) {
                            visitedCells.add(it)
                            distances[it] = d + 1
                        }
                    }
                }
            }
            d += 1
//            if (d >= field.height * field.width) {
//                for (row in field.height - 1 downTo 0) {
//                    for (col in 0 until field.width) {
//                        print(distances[listOf(row, col)].toString() + "\t")
//                    }
//                    println()
//                }
//                val logger = Logger.getAnonymousLogger()
//                logger.info("Пытался найти путь из [${position[0]},${position[0]}] в [$goal,x]. Найден ли путь: ${isPathExists(position, obstacles, goal)}")
//                //logger.info("Параметры функции:\nobstacles: $obstacles ,\nopponent: $opponent,\ncan step on opponent: $canStepOnOpponent")
//                throw Exception("Слишком длинный путь!")
//            }
        }
        val lastCell = visitedCells.filter { it[0] == goal }
        assert(lastCell.size == 1)
        var current = lastCell[0]
        result.add(current)
        while (d > 0) {
            val nearbyCells = expandPlayer(current, obstacles, opponent, false) ?: throw NullPointerException()
            for (i in nearbyCells) {
                if (distances[i] == d - 1) {
                    current = i
                    result.add(i)
                    break
                }
            }
            d -= 1
        }
        result.reverse()
//        println("Найденный путь из [${position[0]},${position[1]}] в [${result.last()[0]},${result.last()[1]}] длины ${result.size}:")
//        for (cell in result) {
//            print("[${cell[0]},${cell[1]}] - ")
//        }
//        println()
        return result
    }

    private fun expandObstacles(obstacles: Set<Array<Array<Int>>>) : Set<Array<Array<Int>>>{
        val res = mutableSetOf<Array<Array<Int>>>()
        for (i in 0 until field.height) {
            for (j in 0 until  field.width) {
                for (k in 0 until 8){
                    val obstacle = getObstacle(i, j, k)
                    if (!isValidObstacle(obstacle, field.width, field.height)) {
                        continue
                    }
                    if (stepOverObstacles(obstacle[0][0], obstacle[0][1], obstacle[1][0], obstacle[1][1], field.barriers) || stepOverObstacles(obstacle[2][0], obstacle[2][1], obstacle[3][0], obstacle[3][1], field.barriers)) {
                        continue
                    }
                    val newSetBarriers = mutableSetOf<Array<Array<Int>>>()
                    newSetBarriers.addAll(obstacles)
                    newSetBarriers.add(obstacle)
                    if (!isPathExists(field.position, newSetBarriers, goal) || !isPathExists(field.opponentPosition, newSetBarriers, field.height - 1 - goal)) {
                        continue
                    }
                    res.add(obstacle)
                }
            }
        }
//        println("Expand obstacles ended")
        return res
    }

    private fun isPathExists(position: List<Int>, barriers: Set<Array<Array<Int>>>, goal: Int): Boolean {
        if (position[0] == goal) return true
        val queue = LinkedList(mutableListOf(position))
        val visitedCells = mutableListOf<List<Int>>()
        while (!queue.isEmpty()) {
            val current = queue.pop()
            expandPlayer(current, barriers, field.position, true)?.forEach {
                if (it[0] == goal) return true
                if (it!=current && !visitedCells.contains(it)) {
                    queue.push(it)
                    visitedCells.add(it)
                }}
        }
        return false
    }

    private fun isValidObstacle(obstacle: Array<Array<Int>>, width: Int, height: Int): Boolean {
        if (obstacle[0][0] < 0 || obstacle[0][0] >= height || obstacle[0][1] < 0 || obstacle[0][1] >= width ||
                obstacle[1][0] < 0 || obstacle[1][0] >= height || obstacle[1][1] < 0 || obstacle[1][1] >= width ||
                obstacle[2][0] < 0 || obstacle[2][0] >= height || obstacle[2][1] < 0 || obstacle[2][1] >= width ||
                obstacle[3][0] < 0 || obstacle[3][0] >= height || obstacle[3][1] < 0 || obstacle[3][1] >= width) {
            return false
        }
        return true
    }

    private fun getObstacle(i: Int, j: Int, k: Int): Array<Array<Int>> {
        return when(k){
            0 -> arrayOf(arrayOf(i, j), arrayOf(i + 1, j), arrayOf(i, j - 1), arrayOf(i + 1, j - 1))
            1 -> arrayOf(arrayOf(i, j), arrayOf(i + 1, j), arrayOf(i, j + 1), arrayOf(i + 1, j + 1))
            2 -> arrayOf(arrayOf(i, j), arrayOf(i - 1, j), arrayOf(i, j - 1), arrayOf(i - 1, j - 1))
            3 -> arrayOf(arrayOf(i, j), arrayOf(i - 1, j), arrayOf(i, j + 1), arrayOf(i - 1, j + 1))
            4 -> arrayOf(arrayOf(i, j), arrayOf(i, j - 1), arrayOf(i + 1, j), arrayOf(i + 1, j - 1))
            5 -> arrayOf(arrayOf(i, j), arrayOf(i, j + 1), arrayOf(i + 1, j), arrayOf(i + 1, j + 1))
            6 -> arrayOf(arrayOf(i, j), arrayOf(i, j - 1), arrayOf(i - 1, j), arrayOf(i - 1, j - 1))
            7 -> arrayOf(arrayOf(i, j), arrayOf(i, j + 1), arrayOf(i - 1, j), arrayOf(i - 1, j + 1))
            else -> arrayOf(arrayOf(i, j), arrayOf(i + 1, j), arrayOf(i, j - 1), arrayOf(i + 1, j - 1))
        }
    }

    //Возвращаем список клеток, куда мы можем сходить или null, если некуда
    private fun expandMoves(position: List<Int> = field.position, obstacles: Set<Array<Array<Int>>> = field.barriers) : List<List<Int>>?{
        //Потенциально можем сходить в 4 клетки, вверх, вниз, влево, вправо
        var res = listOf(
            listOf(position[0] - 1, position[1]),
            listOf(position[0] + 1, position[1]),
            listOf(position[0], position[1] - 1),
            listOf(position[0], position[1] + 1)
        )
        //Отсеиваем (filter) те ходы из 4, которые нельзя сделать
        res = res.filter { isLegalMove(position, it, obstacles) }
        //Возвращаем те, что остались или null
        return if (res.isEmpty()) null else res
    }

    private fun expandPlayer(position: List<Int>, obstacles: Set<Array<Array<Int>>>, opponent: List<Int>, canStepOnOpponent: Boolean) : List<List<Int>>?{
        //Потенциально можем сходить в 4 клетки, вверх, вниз, влево, вправо
        var res = listOf(
            listOf(position[0] - 1, position[1]),
            listOf(position[0] + 1, position[1]),
            listOf(position[0], position[1] - 1),
            listOf(position[0], position[1] + 1)
        )
        //Отсеиваем (filter) те ходы из 4, которые нельзя сделать
        res = res.filter { canPlayerMove(position, it, obstacles, opponent, canStepOnOpponent) }
        //Возвращаем те, что остались или null
        return if (res.isEmpty()) null else res
    }

    private fun canPlayerMove(from: List<Int>, to: List<Int>, obstacles: Set<Array<Array<Int>>>, opponent: List<Int>, canStepOnOpponent: Boolean): Boolean {
        if (!isInBoard(to[0], to[1])) return false
        //Запрыгнуть на оппонента тоже нельзя
        if (!canStepOnOpponent && to[0] == opponent[0] && to[1] == opponent[1]) return false
        //Если пересекаем препятствие - нельзя
        if (stepOverObstacles(from[0], from[1], to[0], to[1], obstacles)) return false
        return true
    }

    //Проверяем, находится ли точка (х, у) на поле
    private fun isInBoard(x: Int, y: Int) : Boolean {
        return x in 0 until field.height && y in 0 until field.width
    }

    //Проверяем, можно ли нам сходить из from  в to
    private fun isLegalMove(from: List<Int>, to: List<Int>, obstacles: Set<Array<Array<Int>>>) : Boolean {
        //Если конечная точка вне доски - нельзя
        if (!isInBoard(to[0], to[1])) return false
        //Запрыгнуть на оппонента тоже нельзя
        if (to[0] == field.opponentPosition[0] && to[1] == field.opponentPosition[1]) return false
        //Если пересекаем препятствие - нельзя
        if (stepOverObstacles(from[0], from[1], to[0], to[1], obstacles)) return false
        return true
    }

    private fun stepOverObstacles(fromX: Int, fromY: Int, toX: Int, toY: Int, obstacles: Set<Array<Array<Int>>>): Boolean {
        return obstacles.any {
            it[0][0] == fromX && it[0][1] == fromY && it[1][0] == toX && it[1][1] == toY ||
                    it[0][0] == toX && it[0][1] == toY && it[1][0] == fromX && it[1][1] == fromY ||
                    it[2][0] == fromX && it[2][1] == fromY && it[3][0] == toX && it[3][1] == toY ||
                    it[2][0] == toX && it[2][1] == toY && it[3][0] == fromX && it[3][1] == fromY
        }
    }

    fun addSendListener(sendFunction: (String)->Unit) {
        send.add(sendFunction)
    }

    fun addReceiveListener(receiveFunction: KFunction0<String>) {
        receive.add(receiveFunction)
    }
}