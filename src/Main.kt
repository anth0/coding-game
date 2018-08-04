import Ability.*
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
            commands.add(Pick(efficientIndexCard))
        } else { // FIGHT

            if (!findLethal(gameState, commands)) {

                var potentialCardsToPlay: List<Card> = gameState.hand
                        .filter { card -> card.cost <= gameState.me().mana }
                        .sortedByDescending { card -> card.cost }

                System.err.println(potentialCardsToPlay)

                //var availableMana = gameState.me().mana
                while (potentialCardsToPlay.isNotEmpty() && gameState.me().mana >= 0) {
                    potentialCardsToPlay = potentialCardsToPlay.dropWhile { card -> card.cost > gameState.me().mana }
                    val cardToPlay: Card? = potentialCardsToPlay.sortedByDescending { card -> card.abilities.size }.firstOrNull()
                    if (cardToPlay != null) {
                        when (cardToPlay.type) {
                            CREATURE -> commands.add(summonCreature(cardToPlay, gameState))
                            GREEN_ITEM ->  {
                                val command = useGreenItem(cardToPlay, gameState)
                                if (command != null) {
                                    commands.add(command)
                                }
                            }
                            RED_ITEM -> {
                                val command = useRedItem(cardToPlay, gameState)
                                if (command != null) {
                                    commands.add(command)
                                }
                            }
                            BLUE_ITEM -> commands.add(useItem(cardToPlay, gameState))
                        }
                        potentialCardsToPlay = potentialCardsToPlay.drop(1)
                    }
                }

                // TODO save card already play

                val opponentGuard = gameState.board.opponentCards.firstOrNull { card -> card.abilities.contains(GUARD) }
//TODO attack more intelligently by making better trades
                gameState.board.myCards.forEach { card: Card ->
                    commands.add(Attack(card.instanceId, opponentGuard?.instanceId ?: -1))
                }
            }
        }

        commands.execute()
    }
}

fun useGreenItem(cardToPlay: Card, gameState: State): Command? {
    if (gameState.board.myCards.isEmpty()) {
        return null
    }

    if (cardToPlay.hasAbilities()) {
        // then put it on one of our guards that does not already have those abilities
        val targetGuard = gameState.board.myCards
                .filter { card -> card.abilities.contains(GUARD) }
                .firstOrNull { guard -> guard.abilities.intersect(cardToPlay.abilities).isEmpty() }
        var targetCard = targetGuard

        if (targetGuard == null) {
            // then pick any card in play
            targetCard = gameState.board.myCards.firstOrNull { card -> card.abilities.intersect(cardToPlay.abilities).isEmpty() }
        }

        if (targetCard != null) {
            return useItem(cardToPlay, gameState, targetCard.instanceId)
        } else {
            System.err.println("Found no suitable target for item ${cardToPlay.instanceId}")
        }
    } else {
        // boost one of our cards
        val targetCard = gameState.board.myCards.first()
        return useItem(cardToPlay, gameState, targetCard.instanceId)
    }

    return null
}

fun useRedItem(cardToPlay: Card, gameState: State): Command? {
    val targetCard: Card?
    // If item removes an ability then find an opponent's card with that ability
    targetCard = if (cardToPlay.hasAbilities()){
        //TODO pick the best intersection (most abilities removed and max dmg) instead of the first matching
        // for example don't pick a card with only guard if our red item can remove all abilities
        gameState.board.opponentCards.sortedByDescending { card -> card.defense }.firstOrNull { card -> card.abilities.intersect(cardToPlay.abilities).isNotEmpty() }

    } else {
        // If item can kill an opponent's card then target it
        gameState.board.opponentCards.sortedByDescending { card -> card.defense }.firstOrNull { card -> card.defense <= cardToPlay.attack }
    }

    return if (targetCard != null) {
        useItem(cardToPlay, gameState, targetCard.instanceId)
    } else {
        System.err.println("Found no suitable target for item ${cardToPlay.instanceId}")
        null
    }
}

fun findLethal(gameState: State, commands: Commands): Boolean {
    // calculate lethal TODO don't forget WARD capacity on GUARD
    val opponentHealth = gameState.opponent().health
    val totalDefenseOfGuards = gameState.board.opponentCards
            .filter { card -> card.abilities.contains(GUARD) }
            .sumBy { card -> card.defense }
    val opponentHasGuards = totalDefenseOfGuards > 0
    val damageOnBoard = gameState.board.myCards.sumBy { card: Card -> card.attack }

    if (opponentHasGuards) {
        // TODO
        // clear board by red card
        gameState.hand
                .filter { card: Card -> card.type == RED_ITEM }
    } else {
        if (opponentHealth <= damageOnBoard) {
            System.err.println("CAN FINISH HIM OFF")
            gameState.board.myCards.forEach { card: Card -> commands.add(Attack(card.instanceId)) }
            return true
        } else {
            val healthLeft = opponentHealth - damageOnBoard

            val buffCards = gameState.hand.filter { card: Card -> card.type == GREEN_ITEM && card.attack > 0 }
            var dmgWithBuff = 0
            if (gameState.board.myCards.isNotEmpty()) {
                dmgWithBuff = buffCards.sumBy { card: Card -> card.attack }
            }

            val chargeCards = gameState.hand.filter { card -> card.abilities.contains(CHARGE) }
            val dmgWithCharge = chargeCards.sumBy { card: Card -> card.attack }

            val spellCards = gameState.hand.filter { card: Card -> card.type == BLUE_ITEM }
            // negative value
            val dmgWithSpell = -spellCards.sumBy { card: Card -> card.defense + card.opponentHealthChange }

            val dmgMax = dmgWithBuff + dmgWithCharge + dmgWithSpell
            System.err.println("Max possible damage right now: $dmgMax. Health left: $healthLeft")
            if (dmgMax >= healthLeft) {
                // calculate mana possibilities
                val cardToPlay: Card? = searchPossibilities(gameState.me().mana, healthLeft, buffCards + chargeCards + spellCards)
                if (cardToPlay != null) {
                    when (cardToPlay.type) {
                        CREATURE -> commands.add(summonCreature(cardToPlay, gameState))
                        GREEN_ITEM -> commands.add(useItem(cardToPlay, gameState, gameState.board.myCards.first().instanceId))
                        BLUE_ITEM -> commands.add(useItem(cardToPlay, gameState))
                        RED_ITEM -> commands.add(useItem(cardToPlay, gameState))
                    }
                    gameState.board.myCards.forEach { card: Card -> commands.add(Attack(card.instanceId, -1)) }
                }
            }
        }
    }

    return false
}

fun searchPossibilities(mana: Int, healthLeft: Int, cards: List<Card>): Card? {
    // TODO calculate for multiple card combo

    // one card combo
    val possibilities = cards.filter { card: Card ->
        when (card.type) {
            GREEN_ITEM, CREATURE -> card.attack >= healthLeft && card.cost <= mana
            BLUE_ITEM -> (card.defense + card.opponentHealthChange) >= healthLeft && card.cost <= mana
            RED_ITEM -> false
        }
    }
    return possibilities.firstOrNull()
}

fun useItem(cardToPlay: Card, gameState: State, targetId: Int = -1): Command {
    gameState.me().mana -= cardToPlay.cost
    gameState.hand.remove(cardToPlay)
    return Use(cardToPlay.instanceId, targetId)
}

fun summonCreature(cardToPlay: Card, gameState: State): Command {
    if (cardToPlay.abilities.contains(CHARGE)) {
        gameState.board.myCards.add(cardToPlay)
    }
    gameState.me().mana -= cardToPlay.cost
    gameState.hand.remove(cardToPlay)
    return Summon(cardToPlay.instanceId)
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
        val cardType = CardType.values()[input.nextInt()]
        val cost = input.nextInt()
        val attack = input.nextInt()
        val defense = input.nextInt()
        val abilities= input.next().filter { char -> char != '-' }.map { ability -> Ability.fromCode(ability.toString()) }
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

abstract class Card(val id: Int, val instanceId: Int, val location: Int, val type: CardType, val cost: Int, val attack: Int, val defense: Int, val abilities: List<Ability>,
                    val myHealthChange: Int, val opponentHealthChange: Int, val cardDraw: Int, var played: Boolean = false) {
    override fun toString(): String = instanceId.toString()
    fun hasAbilities(): Boolean {
        return abilities.isNotEmpty()
    }
}

abstract class Item(id: Int, instanceId: Int, location: Int, type: CardType, cost: Int, attack: Int, defense: Int, abilities: List<Ability>, myHealthChange: Int, opponentHealthChange: Int, cardDraw: Int) : Card(id, instanceId, location, type, cost, attack, defense, abilities, myHealthChange, opponentHealthChange, cardDraw)
class Creature(id: Int, instanceId: Int, location: Int, cost: Int, attack: Int, defense: Int, abilities: List<Ability>, myHealthChange: Int, opponentHealthChange: Int, cardDraw: Int) : Card(id, instanceId, location, CREATURE, cost, attack, defense, abilities, myHealthChange, opponentHealthChange, cardDraw)
class GreenItem(id: Int, instanceId: Int, location: Int, cost: Int, attack: Int, defense: Int, abilities: List<Ability>, myHealthChange: Int, opponentHealthChange: Int, cardDraw: Int) : Item(id, instanceId, location, GREEN_ITEM, cost, attack, defense, abilities, myHealthChange, opponentHealthChange, cardDraw)
class RedItem(id: Int, instanceId: Int, location: Int, cost: Int, attack: Int, defense: Int, abilities: List<Ability>, myHealthChange: Int, opponentHealthChange: Int, cardDraw: Int) : Item(id, instanceId, location, RED_ITEM, cost, attack, defense, abilities, myHealthChange, opponentHealthChange, cardDraw)
class BlueItem(id: Int, instanceId: Int, location: Int, cost: Int, attack: Int, defense: Int, abilities: List<Ability>, myHealthChange: Int, opponentHealthChange: Int, cardDraw: Int) : Item(id, instanceId, location, BLUE_ITEM, cost, attack, defense, abilities, myHealthChange, opponentHealthChange, cardDraw)
enum class Ability(val code: String) {
    CHARGE("C"), BREAKTHROUGH("B"), GUARD("G"), DRAIN("D"), LETHAL("L"), WARD("W");

    companion object {
        fun fromCode(c: String): Ability {
            return values().first { ability -> ability.code == c }
        }
    }
}

enum class CardType {
    CREATURE, GREEN_ITEM, RED_ITEM, BLUE_ITEM
}


//Green items should target the active player's creatures. They have a positive effect on them.
//Red items should target the opponent's creatures. They have a negative effect on them.
//Blue items can be played with the "no creature" target identifier (-1) to give the active player a positive effect or cause damage to the opponent, depending on the card. Blue items with negative defense points can also target enemy creatures.

class Commands(private var commands: MutableList<Command>) {
    fun execute() {
        if (commands.isEmpty()) {
            add(Pass())
        }
        println(commands.joinToString(";"))
    }

    fun add(command: Command) {
        commands.add(command)
    }

    fun reinit() {
        commands.clear()
    }
}

abstract class Command

class Pick(private val cardId: Int) : Command() {
    override fun toString(): String {
        return "PICK $cardId"
    }
}

class Summon(private val instanceId: Int) : Command() {
    override fun toString(): String {
        return "SUMMON $instanceId"
    }
}

class Pass : Command() {
    override fun toString(): String {
        return "PASS"
    }
}

class Attack(private val attackerId: Int, private var opponentId: Int = -1) : Command() {
    override fun toString(): String {
        return "ATTACK $attackerId $opponentId"
    }
}

class Use(private val itemId: Int, private val targetId: Int = -1) : Command() {
    override fun toString(): String {
        return "USE $itemId $targetId"
    }
}