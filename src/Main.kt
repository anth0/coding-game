import Ability.*
import CardType.*
import java.util.*

const val TIMEOUT = 95

/**
 * Auto-generated code below aims at helping you parse
 * the standard input according to the problem statement.
 **/
fun main(args: Array<String>) {
    val bot = Bot()

    // game loop
    while (true) {
        bot.read()
        bot.think()
        bot.write()
    }
}

fun playSimulation(simulation: GameSimulation) {

    val gameState = simulation.gameState
    val actionPlan = simulation.actionPlan

    // Summon cards and use items
    var hasCardsToSummon = gameState.hand.any { it.cost <= gameState.me().mana && !it.analyzed }
    while (hasCardsToSummon) {
        val card = gameState.hand.filter { it.cost <= gameState.me().mana && !it.analyzed }.getRandomElement()
        when (card) {
            is Creature -> actionPlan.add(summonCreature(card, gameState))
            is RedItem -> {
                if (gameState.board.opponentCards.size > 0) {
                    actionPlan.add(useItem(card, gameState, gameState.board.opponentCards.getRandomElement().instanceId))
                } else {
                    card.analyzed = true
                }
            }
            is GreenItem -> {
                if (gameState.board.myCards.size > 0) {
                    actionPlan.add(useItem(card, gameState, gameState.board.myCards.getRandomElement().instanceId))
                } else {
                    card.analyzed = true
                }
            }
            is BlueItem -> {
                val targetId = if (gameState.board.opponentCards.size > 0) gameState.board.opponentCards.getRandomElement().instanceId else -1
                actionPlan.add(useItem(card, gameState, targetId))
            }
        }
        hasCardsToSummon = gameState.hand.any { it.cost <= gameState.me().mana && !it.analyzed }
    }

    // Attack
    var hasCreatureToPlay = gameState.board.myCards.any { !it.played && it.attack > 0 }
    while (hasCreatureToPlay) {
        val creature = gameState.board.myCards.filter { !it.played && it.attack > 0 }.getRandomElement()
        var target: Creature? = null
        if (gameState.board.opponentCards.size != 0) {
            if (gameState.board.opponentCards.hasGuards()) {
                target = gameState.board.opponentCards.guards().getRandomElement()
            } else {
                // Fetching random int from 0 to size + 1 to have an index also for enemy hero
                // Then Int which is out of bound represents the enemy hero
                val randomTargetIndex = Random().nextInt(gameState.board.opponentCards.size + 1)
                if (randomTargetIndex <= gameState.board.opponentCards.size) {
                    gameState.board.opponentCards.getRandomElement()
                }
            }
        }
        actionPlan.add(attack(creature, target, gameState))

        hasCreatureToPlay = gameState.board.myCards.any { !it.played && it.attack > 0 }
    }

    // Be attacked
    var enemyHasCreatureToPlay = gameState.board.opponentCards.any { !it.played && it.attack > 0 }
    while (enemyHasCreatureToPlay) {
        val creature = gameState.board.opponentCards.filter { !it.played && it.attack > 0 }.getRandomElement()
        var target: Creature? = null
        if (gameState.board.myCards.size != 0) {
            if (gameState.board.myCards.hasGuards()) {
                target = gameState.board.myCards.guards().getRandomElement()
            } else {
                // Fetching random int from 0 to size + 1 to have an index also for enemy hero
                // Then Int which is out of bound represents the enemy hero
                val randomTargetIndex = Random().nextInt(gameState.board.myCards.size + 1)
                if (randomTargetIndex <= gameState.board.myCards.size) {
                    gameState.board.myCards.getRandomElement()
                }
            }
        }
        attackMyself(creature, target, gameState)

        enemyHasCreatureToPlay = gameState.board.opponentCards.any { !it.played && it.attack > 0 }
    }
}

fun useItem(item: Item, gameState: State, targetId: Int = -1): Action {
    gameState.me().mana -= item.cost
    gameState.hand.remove(item)
    return Use(item.instanceId, targetId)
}

fun attack(attacker: Creature, target: Creature?, gameState: State): Action {
    attacker.played = true

    if (target == null) {
        gameState.opponent().health -= attacker.attack
    } else {
        if (attacksKillTarget(attacker, target)) {
            gameState.board.opponentCards.remove(target)
        } else if (target.abilities.contains(Ability.WARD)) {
            gameState.board.opponentCards.find { card -> card.id == target.id }?.abilities?.remove(WARD)
        } else if (!target.abilities.contains(Ability.WARD)) {
            gameState.board.opponentCards.first { card -> card.id == target.id }.defense -= attacker.attack
        }
    }

    return Attack(attacker.instanceId, target?.instanceId ?: -1)
}

fun attackMyself(attacker: Creature, target: Creature?, gameState: State) {
    attacker.played = true

    if (target == null) {
        gameState.me().health -= attacker.attack
    } else {
        if (attacksKillTarget(attacker, target)) {
            gameState.board.myCards.remove(target)
        } else if (target.abilities.contains(Ability.WARD)) {
            gameState.board.myCards.find { card -> card.id == target.id }?.abilities?.remove(WARD)
        } else if (!target.abilities.contains(Ability.WARD)) {
            gameState.board.myCards.first { card -> card.id == target.id }.defense -= attacker.attack
        }
    }
}

fun summonCreature(cardToPlay: Creature, gameState: State): Action {
    if (cardToPlay.abilities.contains(CHARGE)) {
        gameState.board.myCards.add(cardToPlay)
    }
    gameState.me().mana -= cardToPlay.cost
    gameState.hand.remove(cardToPlay)
    return Summon(cardToPlay.instanceId)
}

fun attacksKillTarget(attacker: Card, target: Card): Boolean {
    return !target.abilities.contains(WARD) && (attacker.abilities.contains(LETHAL) || attacker.attack >= target.defense)
}

fun searchMostRatedCard(state: State): Int {
    return arrayListOf(state.hand[0], state.hand[1], state.hand[2]).map { getCardRating(it) }.indexOfMax()
}

val repartition = mutableListOf(2, 7, 6, 7, 4, 2, 1, 1)
fun searchEfficientCurve(state: State): Int {
    val needItem = state.deck.filter { card -> card.type != CREATURE }.size < state.deck.size / 5
    val firstCard = state.hand[0]
    val secondCard = state.hand[1]
    val thirdCard = state.hand[2]
    val bestEffectiveCard = arrayOf(firstCard, secondCard, thirdCard)
            .maxBy { card -> repartition[Math.min(card.cost, 7)] + getCardRating(card) + if (needItem && card.type != CREATURE) 10 else 0 }

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

fun getCardRating(card: Card): Double {
    val gain = (Math.abs(card.attack) + Math.abs(card.defense) + getAbilitiesRating(card) + (card.myHealthChange / 2) - (card.opponentHealthChange / 2) + (card.cardDraw * 2))
    val cost = if (card.cost == 0) 1 else card.cost
    val finalRating = gain / cost / 62 * 10
    //log("Card $card rated $finalRating")
    return finalRating
}

fun getAbilitiesRating(card: Card): Double {
    var rating = 0.0
    if (card.abilities.contains(BREAKTHROUGH)) {
        rating += 1
    }
    if (card.abilities.contains(CHARGE)) {
        rating += 2
    }
    if (card.abilities.contains(DRAIN)) {
        rating += 0.5 * card.attack
    }
    if (card.abilities.contains(GUARD)) {
        rating += 1
    }
    if (card.abilities.contains(LETHAL)) {
        rating += 3
    }
    if (card.abilities.contains(WARD)) {
        rating += 2 + card.attack
    }
    return rating
}


/*****************************************************************************************************
 ******************************************   MODELS **************************************************
 ******************************************************************************************************/

class Bot {
    private val input = Scanner(System.`in`)

    private lateinit var state: State
    private lateinit var actionPlan: ActionPlan

    fun read() {
        state = State()
        state.players = listOf(
                Player(input.nextInt(), input.nextInt(), input.nextInt(), input.nextInt()),
                Player(input.nextInt(), input.nextInt(), input.nextInt(), input.nextInt()))

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
            val abilities = input.next().filter { char -> char != '-' }.map { ability -> Ability.fromCode(ability.toString()) }.toMutableList()
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
                0 -> state.hand.add(card)
                1 -> state.board.myCards.add(card as Creature)
                -1 -> state.board.opponentCards.add(card as Creature)
            }
        }
    }

    fun think() {
        actionPlan = ActionPlan(mutableListOf())

        if (state.isInDraftPhase()) {
            //val bestCardIndex = searchMostRatedCard(firstCard, secondCard, thirdCard, deck)
            val efficientIndexCard = searchEfficientCurve(state)
            state.deck.add(state.hand[efficientIndexCard])
            actionPlan.add(Pick(efficientIndexCard))
        } else {
            var bestActionPlan = actionPlan
            Benchmark.runUntil(TIMEOUT) {
                val simulation = GameSimulation(state.copy())
                playSimulation(simulation)
                simulation.eval()

                if (simulation.actionPlan.score > bestActionPlan.score) {
                    bestActionPlan = simulation.actionPlan
                }
            }
            actionPlan = bestActionPlan
            log("best score is ${bestActionPlan.score}")
        }
    }

    fun write() {
        actionPlan.execute()
    }
}

class GameSimulation(
        val gameState: State,
        val actionPlan: ActionPlan = ActionPlan(mutableListOf())) {

    fun eval() {

        if (gameState.opponent().health <= 0) {
            actionPlan.score = Double.MAX_VALUE
            return
        }

        // My board
        actionPlan.score = gameState.board.myCards.sumByDouble { getCardRating(it) } * 2

        // Opponent's board
        actionPlan.score -= gameState.board.opponentCards.sumByDouble { getCardRating(it) } * 2

        // My health
        actionPlan.score += (gameState.me().health / 3)

        // Opponent's health
        actionPlan.score -= gameState.opponent().health

        // My deck size compared to the enemy's
        actionPlan.score += gameState.me().deckSize - gameState.opponent().deckSize

        // Nb of cards in hand
        actionPlan.score += (gameState.hand.size)
    }
}

data class State(
        val board: Board = Board(mutableListOf(), mutableListOf()),
        var players: List<Player> = listOf(),
        var hand: MutableList<Card> = mutableListOf(),
        var deck: MutableList<Card> = mutableListOf()) {

    fun me(): Player {
        return players[0]
    }

    fun opponent(): Player {
        return players[1]
    }

    fun isInDraftPhase(): Boolean {
        return players[0].mana <= 0
    }
}

data class Board(val myCards: MutableList<Creature>, val opponentCards: MutableList<Creature>) {}

data class Player(var health: Int, var mana: Int, var deckSize: Int, var runes: Int) {
    fun update(newHealth: Int, newMana: Int, newDeckSize: Int, newRunes: Int) {
        health = newHealth
        mana = newMana
        deckSize = newDeckSize
        runes = newRunes
    }
}

abstract class Card(val id: Int, val instanceId: Int, val location: Int, val type: CardType, val cost: Int, var attack: Int, var defense: Int, val abilities: MutableList<Ability>,
                    val myHealthChange: Int, val opponentHealthChange: Int, val cardDraw: Int, var played: Boolean = false) {
    var analyzed = false

    override fun toString(): String = instanceId.toString()
    fun hasAbilities(): Boolean {
        return abilities.isNotEmpty()
    }
}

abstract class Item(id: Int, instanceId: Int, location: Int, type: CardType, cost: Int, attack: Int, defense: Int, abilities: MutableList<Ability>, myHealthChange: Int, opponentHealthChange: Int, cardDraw: Int) : Card(id, instanceId, location, type, cost, attack, defense, abilities, myHealthChange, opponentHealthChange, cardDraw)
class Creature(id: Int, instanceId: Int, location: Int, cost: Int, attack: Int, defense: Int, abilities: MutableList<Ability>, myHealthChange: Int, opponentHealthChange: Int, cardDraw: Int) : Card(id, instanceId, location, CREATURE, cost, attack, defense, abilities, myHealthChange, opponentHealthChange, cardDraw)
class GreenItem(id: Int, instanceId: Int, location: Int, cost: Int, attack: Int, defense: Int, abilities: MutableList<Ability>, myHealthChange: Int, opponentHealthChange: Int, cardDraw: Int) : Item(id, instanceId, location, GREEN_ITEM, cost, attack, defense, abilities, myHealthChange, opponentHealthChange, cardDraw)
class RedItem(id: Int, instanceId: Int, location: Int, cost: Int, attack: Int, defense: Int, abilities: MutableList<Ability>, myHealthChange: Int, opponentHealthChange: Int, cardDraw: Int) : Item(id, instanceId, location, RED_ITEM, cost, attack, defense, abilities, myHealthChange, opponentHealthChange, cardDraw)
class BlueItem(id: Int, instanceId: Int, location: Int, cost: Int, attack: Int, defense: Int, abilities: MutableList<Ability>, myHealthChange: Int, opponentHealthChange: Int, cardDraw: Int) : Item(id, instanceId, location, BLUE_ITEM, cost, attack, defense, abilities, myHealthChange, opponentHealthChange, cardDraw)
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

class ActionPlan(private var actions: MutableList<Action>) {
    var score = Double.NEGATIVE_INFINITY

    fun execute() {
        if (actions.isEmpty()) {
            log("No action found in the action plan!")
            add(Pass())
        }
        println(actions.joinToString(";"))
    }

    fun add(action: Action) {
        actions.add(action)
    }
}

abstract class Action

class Pick(private val cardId: Int) : Action() {
    override fun toString(): String {
        return "PICK $cardId"
    }
}

class Summon(private val instanceId: Int) : Action() {
    override fun toString(): String {
        return "SUMMON $instanceId"
    }
}

class Pass : Action() {
    override fun toString(): String {
        return "PASS"
    }
}

class Attack(private val attackerId: Int, private var opponentId: Int = -1) : Action() {
    override fun toString(): String {
        return "ATTACK $attackerId $opponentId"
    }
}

class Use(private val itemId: Int, private val targetId: Int = -1) : Action() {
    override fun toString(): String {
        return "USE $itemId $targetId"
    }
}

class Benchmark {
    companion object {
        fun logTime(text: String, block: () -> Unit) {
            val startTime = System.currentTimeMillis()
            block.invoke()
            val duration = System.currentTimeMillis() - startTime
            System.err.println("$text -> $duration ms")
        }

        fun runUntil(timeout: Int, block: () -> Unit) {
            val startTime = System.currentTimeMillis()
            var iterations = 0
            while (System.currentTimeMillis() - startTime < timeout) {
                block.invoke()
                iterations++
            }
            log("$iterations simulations")
        }
    }
}

/*****************************************************************************************************
 ******************************************   UTILS **************************************************
 ******************************************************************************************************/

fun <T : Double> Iterable<T>.indexOfMax(): Int {
    var maxIndex = 0

    for ((index, elem) in this.withIndex()) {
        val newElem = this.elementAt(index)
        if (newElem >= this.elementAt(maxIndex)) {
            maxIndex = index
        }
    }
    return maxIndex
}

fun <E> List<E>.getRandomElement() = this[Random().nextInt(this.size)]
fun <T : Creature> List<T>.hasGuards(): Boolean = this.any { it.abilities.contains(GUARD) }
fun <T : Creature> List<T>.guards(): List<T> = this.filter { it.abilities.contains(GUARD) }

fun log(text: String) {
    System.err.println(text)
}