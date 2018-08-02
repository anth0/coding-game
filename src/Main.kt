import CardType.*
import java.util.*

const val MAX_MANA = 12
val repartition = mutableListOf(0, 2, 5, 6, 7, 5, 3, 2)

/**
 * Auto-generated code below aims at helping you parse
 * the standard input according to the problem statement.
 **/
fun main(args: Array<String>) {
    val input = Scanner(System.`in`)
    val game = initGame()

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
            deck.add(gameState.hand[efficientIndexCard])
            println("PICK $efficientIndexCard")
        } else { // FIGHT
            var result = ""

            var potentialCardsToPlay: List<Card> = gameState.hand
                    .filter { card -> card.cost <= gameState.me().mana }
                    .sortedByDescending { card -> card.cost }

            System.err.println(potentialCardsToPlay)

            var availableMana = gameState.me().mana
            while (potentialCardsToPlay.isNotEmpty() && availableMana >= 0) {
                val cardToPlay: Card? = potentialCardsToPlay.dropWhile { card -> card.cost > availableMana }.firstOrNull()
                when (cardToPlay?.type) {
                    CREATURE -> result += summonCreature(cardToPlay, gameState)
                    GREEN_ITEM, RED_ITEM, BLUE_ITEM -> useItem(cardToPlay)
                }
                if (cardToPlay != null) {
                    potentialCardsToPlay = potentialCardsToPlay.drop(1)
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

fun useItem(cardToPlay: Card) {
//TODO
}

fun summonCreature(cardToPlay: Card, gameState: State): String {
    if (cardToPlay.abilities.contains("C")) {
        gameState.board.myCards.add(cardToPlay)
    }
    gameState.me().mana -= cardToPlay.cost
    return "SUMMON " + cardToPlay.instanceId + ";" //FIXME convert this to Command
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
        val cardType = values()[input.nextInt()]
        val cost = input.nextInt()
        val attack = input.nextInt()
        val defense = input.nextInt()
        val abilities = input.next()
        val myHealthChange = input.nextInt()
        val opponentHealthChange = input.nextInt()
        val cardDraw = input.nextInt()
        val card = when (cardType) {
            CREATURE -> Creature(cardNumber, instanceId, location, cost, attack, defense, abilities, myHealthChange, opponentHealthChange, cardDraw)
            GREEN_ITEM -> GreenItem(cardNumber, instanceId, location, cost, attack, defense, abilities, myHealthChange, opponentHealthChange, cardDraw)
            RED_ITEM -> RedItem(cardNumber, instanceId, location, cost, attack, defense, abilities, myHealthChange, opponentHealthChange, cardDraw)
            BLUE_ITEM -> BlueItem(cardNumber, instanceId, location, cost, attack, defense, abilities, myHealthChange, opponentHealthChange, cardDraw)
        }

        when (location) {
            0 -> gameState.hand.add(card)
            1 -> gameState.board.myCards.add(card)
            -1 -> gameState.board.opponentCards.add(card)
        }
    }

    return gameState
}

fun searchEfficientCurve(firstCard: Card, secondCard: Card, thirdCard: Card): Int {

    val bestEffectiveCard = arrayOf(firstCard, secondCard, thirdCard)
            .filter { card -> card.type == CREATURE }
            .maxBy { card -> repartition[Math.min(card.cost, 7)] }

    when (bestEffectiveCard) {
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

abstract class Card(val id: Int, val instanceId: Int, val location: Int, val type: CardType, val cost: Int, val attack: Int, val defense: Int, val abilities: String,
                    val myHealthChange: Int, val opponentHealthChange: Int, val cardDraw: Int) {
    override fun toString(): String = instanceId.toString()
}

abstract class Item(id: Int, instanceId: Int, location: Int, type: CardType, cost: Int, attack: Int, defense: Int, abilities: String, myHealthChange: Int, opponentHealthChange: Int, cardDraw: Int) : Card(id, instanceId, location, type, cost, attack, defense, abilities, myHealthChange, opponentHealthChange, cardDraw)
class Creature(id: Int, instanceId: Int, location: Int, cost: Int, attack: Int, defense: Int, abilities: String, myHealthChange: Int, opponentHealthChange: Int, cardDraw: Int) : Card(id, instanceId, location, CREATURE, cost, attack, defense, abilities, myHealthChange, opponentHealthChange, cardDraw)
class GreenItem(id: Int, instanceId: Int, location: Int, cost: Int, attack: Int, defense: Int, abilities: String, myHealthChange: Int, opponentHealthChange: Int, cardDraw: Int) : Item(id, instanceId, location, GREEN_ITEM, cost, attack, defense, abilities, myHealthChange, opponentHealthChange, cardDraw)
class RedItem(id: Int, instanceId: Int, location: Int, cost: Int, attack: Int, defense: Int, abilities: String, myHealthChange: Int, opponentHealthChange: Int, cardDraw: Int) : Item(id, instanceId, location, RED_ITEM, cost, attack, defense, abilities, myHealthChange, opponentHealthChange, cardDraw)
class BlueItem(id: Int, instanceId: Int, location: Int, cost: Int, attack: Int, defense: Int, abilities: String, myHealthChange: Int, opponentHealthChange: Int, cardDraw: Int) : Item(id, instanceId, location, BLUE_ITEM, cost, attack, defense, abilities, myHealthChange, opponentHealthChange, cardDraw)
enum class Ability(val code: String) {
    CHARGE("C"), BREAKTHROUGH("B"), GUARD("G"), DRAIN("D"), LETHAL("L"), WARD("W")
}

enum class CardType {
    CREATURE, GREEN_ITEM, RED_ITEM, BLUE_ITEM
}
