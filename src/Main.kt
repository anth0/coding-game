import java.util.*

const val MAX_MANA = 12
val repartition = arrayOf(0, 2, 5, 6, 7, 5, 3, 2)

/**
 * Auto-generated code below aims at helping you parse
 * the standard input according to the problem statement.
 **/
fun main(args: Array<String>) {
    val input = Scanner(System.`in`)
    var game = initGame()

    // game loop
    while (true) {
        game.nextRound()
        val gameState = updateGameState(game.state, input)
        val deck = gameState.deck

        if (game.isInDraftPhase()) { // DRAFT

            val firstCard = gameState.hand[0]
            val secondCard = gameState.hand[1]
            val thirdCard = gameState.hand[2]
            val efficientIndexCard = searchEfficientCurve(firstCard, secondCard, thirdCard)
//
//            var i = 1
//            for ((index, card) in gameState.hand.withIndex()) {
//                if (card.type == 0) {
//                    i = index
//                }
//                if (card.abilities.contains("C")) {
//                    i = index
//                    break
//                }
//            }
            deck.add(gameState.hand[efficientIndexCard])
            println("PICK $efficientIndexCard")
        } else { // FIGHT
            var result = ""

            var potentialSummons: List<Card> = gameState.hand
                    .filter { card -> card.cost <= gameState.me().mana }
                    .sortedByDescending { card -> card.cost }

            System.err.println(potentialSummons)

            var mana = gameState.me().mana
            while (potentialSummons.isNotEmpty() && mana >= 0) {
                potentialSummons = potentialSummons.dropWhile { card -> card.cost > mana }
                if (potentialSummons.isNotEmpty()) {
                    result += "SUMMON " + potentialSummons[0].instanceId + ";"
                    if (potentialSummons[0].abilities.contains("C")) {
                        gameState.board.myCards.add(potentialSummons[0])
                    }
                    mana -= potentialSummons[0].cost
                    potentialSummons = potentialSummons.drop(1)

                }
            }


            val opponentGuard = gameState.board.opponentCards
                    .filter { card -> card.abilities.contains("G") }
                    .take(1)

            gameState.board.myCards
                    .forEach { card: Card -> result += "ATTACK " + card.instanceId + if (opponentGuard.isEmpty()) " -1;" else (" " + opponentGuard[0].instanceId + ";") }

            if (result == "") {
                println("PASS")
            } else {
                println(result)
            }
        }

    }
}

fun initGame(): Game {
    val players = listOf(
            Player(30, 1, 30, 0),
            Player(30, 1, 30, 0))
    val board = Board(mutableListOf(), mutableListOf())
    val state = State(board, players, mutableListOf(), mutableListOf())

    return Game(0, state)
}

fun updateGameState(gameState: State, input: Scanner): State {

    gameState.me().update(input.nextInt(), input.nextInt(), input.nextInt(), input.nextInt())
    gameState.opponent().update(input.nextInt(), input.nextInt(), input.nextInt(), input.nextInt())

    gameState.hand.clear()
    gameState.board.clear()

    val opponentHandSize = input.nextInt()
    val cardsInPlayCount = input.nextInt()
    for (i in 0 until cardsInPlayCount) {
        val cardNumber = input.nextInt()
        val instanceId = input.nextInt()
        val location = input.nextInt()
        val cardType = input.nextInt()
        val cost = input.nextInt()
        val attack = input.nextInt()
        val defense = input.nextInt()
        val abilities = input.next()
        val myHealthChange = input.nextInt()
        val opponentHealthChange = input.nextInt()
        val cardDraw = input.nextInt()
        val card = Card(cardNumber, instanceId, location, cardType, cost, attack, defense, abilities, myHealthChange, opponentHealthChange, cardDraw)

        when (location) {
            0 -> gameState.hand.add(card)
            1 -> gameState.board.myCards.add(card)
            -1 -> gameState.board.opponentCards.add(card)
        }
    }

    return gameState
}


/*****************************************************************************************************
 ******************************************   MODELS **************************************************
 ******************************************************************************************************/
class Game(private var round: Int, val state: State) {
    fun nextRound() {
        round++
    }

    fun isInDraftPhase(): Boolean {
        return round <= 30
    }

    fun isInFightPhase(): Boolean {
        return round > 30
    }
}

class State(val board: Board, private var players: List<Player>, val hand: MutableList<Card>, val deck: MutableList<Card>) {
    fun me(): Player {
        return players[0]
    }

    fun opponent(): Player {
        return players[1]
    }
}

class Board(val myCards: MutableList<Card>, val opponentCards: MutableList<Card>) {
    fun clear() {
        myCards.clear()
        opponentCards.clear()
    }
}

data class Player(var health: Int, var mana: Int, var deckSize: Int, var runes: Int) {
    fun update(newHealth: Int, newMana: Int, newDeckSize: Int, newRunes: Int) {
        health = newHealth
        mana = newMana
        deckSize = newDeckSize
        runes = newRunes
    }
}

class Card(val id: Int, val instanceId: Int, val location: Int, val type: Int, val cost: Int, val attack: Int, val defense: Int, val abilities: String,
           val myHealthChange: Int, val opponentHealthChange: Int, val cardDraw: Int) {
    override fun toString(): String = instanceId.toString()
}

enum class Ability(val code: String) {
    CHARGE("C"), BREAKTHROUGH("B"), GUARD("G"), DRAIN("D"), LETHAL("L"), WARD("W")
}

fun searchEfficientCurve(firstCard: Card, secondCard: Card, thirdCard: Card): Int {

    val bestEffectiveCard = arrayOf(firstCard, secondCard, thirdCard)
            .filter { card -> card.type == 0 }
            .maxBy { card -> repartition[Math.min(card.cost, 7)] }

    when(bestEffectiveCard) {
        firstCard -> {
            repartition[Math.min(firstCard.cost, 7)] = repartition[Math.min(firstCard.cost, 7)] - 1
            return 0
        }
        secondCard -> {
            repartition[Math.min(secondCard.cost, 7)] = repartition[Math.min(secondCard.cost, 7)] - 1
            return 1
        }
        thirdCard -> {
            repartition[Math.min(thirdCard.cost, 7)] = repartition[Math.min(thirdCard.cost, 7)] - 1
            return 2
        }
    }

    return 0

}