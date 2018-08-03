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
    val commands = Commands(mutableListOf())

    // game loop
    while (true) {
        game.nextRound()
        commands.reinit()
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

            if (!findLethal(gameState, commands)) {

                var potentialCardsToPlay: List<Card> = gameState.hand
                        .filter { card -> card.cost <= gameState.me().mana }
                        .sortedByDescending { card -> card.cost }

                System.err.println(potentialCardsToPlay)

                var availableMana = gameState.me().mana
                while (potentialCardsToPlay.isNotEmpty() && availableMana >= 0) {
                    potentialCardsToPlay = potentialCardsToPlay.dropWhile { card -> card.cost > availableMana }
                    val cardToPlay: Card? = potentialCardsToPlay.firstOrNull()
                    if (cardToPlay != null) {
                        when (cardToPlay.type) {
                            CREATURE -> result += summonCreature(cardToPlay, gameState)
                            GREEN_ITEM, RED_ITEM, BLUE_ITEM -> useItem(cardToPlay)
                        }
                        availableMana -= cardToPlay.cost
                        potentialCardsToPlay = potentialCardsToPlay.drop(1)
                    }
                }

                // TODO save card already play

                val opponentGuard = gameState.board.opponentCards
                        .filter { card -> card.abilities.contains(Ability.GUARD.code) }
                        .take(1)

                gameState.board.myCards
                        .forEach { card: Card -> result += "ATTACK " + card.instanceId + if (opponentGuard.isEmpty()) " -1;" else (" " + opponentGuard[0].instanceId + ";") }

                if (result == "") {
                    println("PASS")
                } else {
                    println(result)
                }
            } else {
                commands.execute()
            }
        }
    }
}

fun findLethal(gameState: State, commands: Commands): Boolean {
    // calculate lethal TODO don't forget WARD capacity on GUARD
    val life = gameState.opponent().health
    val guardOnBoard = gameState.board.opponentCards
            .filter { card -> card.abilities.contains(Ability.GUARD.code) }
            .sumBy { card -> card.defense }

    val damageOnBoard = gameState.board.myCards.sumBy { card: Card -> card.attack }

    if (guardOnBoard == 0) {
        if (life - damageOnBoard <= 0) {
            gameState.board.myCards.forEach { card: Card -> commands.add(Attack(card.instanceId, -1)) }
            return true
        } else {
            val dmgMissing = life - damageOnBoard

            val buffCards = gameState.hand.filter { card: Card -> card.type == GREEN_ITEM && card.attack > 0 }
            var dmgWithBuff = 0
            if (gameState.board.myCards.isNotEmpty()) {
                dmgWithBuff = buffCards.sumBy { card: Card -> card.attack }
            }

            val chargeCards = gameState.hand.filter { card -> card.abilities.contains(Ability.CHARGE.code) }
            val dmgWithCharge = chargeCards.sumBy { card: Card -> card.attack }

            val spellCards = gameState.hand.filter { card: Card -> card.type == BLUE_ITEM }
            // negative value
            val dmgWithSpell = - spellCards.sumBy { card: Card -> card.defense + card.opponentHealthChange }

            val dmgMax = dmgWithBuff + dmgWithCharge + dmgWithSpell

            if (dmgMax >= dmgMissing) {
                // calculate mana possibilities
                val cardToPlay: Card? = searchPossibilities(gameState.me().mana, dmgMissing, buffCards + chargeCards + spellCards)
                if (cardToPlay != null) {
                    when (cardToPlay.type) {
                        CREATURE -> commands.add(Summon(cardToPlay.instanceId))
                        GREEN_ITEM -> commands.add(Use(cardToPlay.instanceId, gameState.board.myCards.firstOrNull()!!.instanceId))
                        BLUE_ITEM -> commands.add(Use(cardToPlay.instanceId, -1))
                    }
                    gameState.board.myCards.forEach { card: Card -> commands.add(Attack(card.instanceId, -1)) }
                }
            }
        }
    }

    if (guardOnBoard > 0) {
        // TODO
        // clear board by red card
        gameState.hand
                .filter { card: Card -> card.type == RED_ITEM }
    }

    return false
}

fun searchPossibilities(mana: Int, dmgMissing: Int, cards: List<Card>): Card? {
    // TODO calculate for multiple card combo

    // one card combo
    val possibilities = cards.filter { card: Card ->
        when(card.type) {
            GREEN_ITEM, CREATURE -> card.attack >= dmgMissing && card.cost <= mana
            BLUE_ITEM -> (card.defense + card.opponentHealthChange) >= dmgMissing && card.cost <= mana
            RED_ITEM -> false
        }
    }
    return possibilities.firstOrNull()
}

fun useItem(cardToPlay: Card) {
//TODO
}

fun summonCreature(cardToPlay: Card, gameState: State): String {
    if (cardToPlay.abilities.contains("C")) {
        gameState.board.myCards.add(cardToPlay)
    }
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

//Green items should target the active player's creatures. They have a positive effect on them.
//Red items should target the opponent's creatures. They have a negative effect on them.
//Blue items can be played with the "no creature" target identifier (-1) to give the active player a positive effect or cause damage to the opponent, depending on the card. Blue items with negative defense points can also target enemy creatures.

class Commands(private var commands: MutableList<Command>) {
    fun execute() {
        println(commands.joinToString(";") { command: Command -> command.execute() })
    }

    fun add(command: Command) {
        commands.add(command)
    }
    fun reinit() {
        commands.clear()
    }
}
abstract class Command {
    abstract fun execute(): String
}
class Pick(val cardId: Int) : Command() {
    override fun execute(): String {
        return "PICK $cardId"
    }
}
class Summon(val instanceId: Int) : Command() {
    override fun execute(): String {
        return "SUMMON $instanceId"
    }
}

class Attack(val instanceId: Int, val target: Int) : Command() {
    override fun execute(): String {
        return "ATTACK $instanceId $target"
    }
}

class Use(val instanceId: Int, val target: Int) : Command() {
    override fun execute(): String {
        return "USE $instanceId $target"
    }
}
