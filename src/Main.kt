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

fun useRedItem(item: Item, target: Creature?, gameState: State): Action {
    if (target == null) {
        gameState.opponent().health += item.opponentHealthChange
    } else {
        gameState.opponent().health += item.opponentHealthChange
        var targetCards = gameState.board.opponentCards

        if (!target.abilities.contains(WARD) && target.defense + item.defense <= 0) {
            targetCards.remove(target)
        } else if (target.abilities.contains(WARD) && item.abilities.contains(WARD) && target.defense + item.defense <= 0) {
            targetCards.remove(target)
        } else if (!target.abilities.contains(WARD)) {
            targetCards.first { it.instanceId == target.instanceId }.apply {
                abilities.removeAll(item.abilities)
                defense += item.defense
                attack += item.attack
            }
        } else if (target.abilities.contains(WARD)) {
            targetCards.first { it.instanceId == target.instanceId }.apply {
                abilities.removeAll(item.abilities)
                attack += item.attack
            }
        }
    }
    return useItem(item, gameState, target?.instanceId ?: -1)
}

fun useGreenItem(item: Item, target: Creature?, gameState: State): Action {
    if (target == null) {
        gameState.me().health += item.myHealthChange
    } else {
        gameState.me().health += item.myHealthChange
        var targetCards = gameState.board.myCards

        targetCards.first { it.instanceId == target.instanceId }.apply {
            attack += item.attack
            defense += item.defense
            abilities.addAll(item.abilities)
        }
    }
    return useItem(item, gameState, target?.instanceId ?: -1)
}

fun useItem(item: Item, gameState: State, targetId: Int = -1): Action {
    gameState.me().mana -= item.cost
    gameState.hand.remove(item)
    return Use(item.instanceId, targetId)
}

fun attack(attacker: Creature, target: Creature?, gameState: State): Action {
    attacker.played = true

    if (target == null) {
        // who is the attacker?
        if (gameState.board.myCards.any { it.instanceId == attacker.instanceId }) {
            gameState.opponent().health -= attacker.attack
        } else {
            gameState.me().health -= attacker.attack
        }
    } else {
        var attackerCards = gameState.board.myCards
        var targetCards = gameState.board.opponentCards

        if (gameState.board.opponentCards.any { it.instanceId == attacker.instanceId }) {
            attackerCards = gameState.board.opponentCards
            targetCards = gameState.board.myCards
        }

        // Update opponent's board
        when {
            attacksKillTarget(attacker, target) -> targetCards.remove(target)
            target.abilities.contains(WARD) && target.attack > 0 -> targetCards.first { it.instanceId == target.instanceId }.apply { this.abilities.remove(WARD) }
            else -> targetCards.first { it.instanceId == target.instanceId }.apply { this.defense -= attacker.attack }
        }

        // Update my board
        when {
            attacksKillTarget(target, attacker) -> attackerCards.remove(attacker)
            target.abilities.contains(WARD) && target.attack > 0 -> attackerCards.first { it.instanceId == attacker.instanceId }.apply { this.abilities.remove(WARD) }
            else -> attackerCards.first { it.instanceId == attacker.instanceId }.apply { this.defense -= target.attack }
        }
    }

    return Attack(attacker.instanceId, target?.instanceId ?: -1)
}

fun attacksKillTarget(attacker: Card, target: Card): Boolean {
    return !target.abilities.contains(WARD) && (attacker.abilities.contains(LETHAL) || attacker.attack >= target.defense)
}

fun summonCreature(cardToPlay: Creature, gameState: State): Action {

    cardToPlay.played = !cardToPlay.abilities.contains(CHARGE)

    gameState.board.myCards.add(cardToPlay)
    gameState.me().mana -= cardToPlay.cost
    gameState.hand.remove(cardToPlay)
    return Summon(cardToPlay.instanceId)
}


fun searchMostRatedCard(state: State): Int {
    return arrayListOf(state.hand[0], state.hand[1], state.hand[2]).map { getCardRating(it) }.indexOfMax()
}

val repartition = mutableListOf(0, 2, 5, 6, 7, 5, 3, 2)
fun searchEfficientCurve(state: State): Int {
    val needItem = state.deck.filter { card -> card.type != CREATURE }.size < state.deck.size / 5
    val firstCard = state.hand[0]
    val secondCard = state.hand[1]
    val thirdCard = state.hand[2]
    val bestEffectiveCard = arrayOf(firstCard, secondCard, thirdCard)
            .maxBy { card -> repartition[Math.min(card.cost, 7)] + getDraftCardRating(card) + if (needItem && card.type != CREATURE) 10 else 0 }

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

fun getDraftCardRating(card: Card): Double {
    val rating: Double = when (card.type) {
        CREATURE, GREEN_ITEM -> card.attack + card.defense + (card.myHealthChange / 2) - (card.opponentHealthChange / 2) + (card.cardDraw * 2) + getAbilitiesRating(card)
        RED_ITEM -> (-card.attack - card.defense + (card.myHealthChange / 2) - (card.opponentHealthChange / 2) + (card.cardDraw * 2)).toDouble() // remove abilities is very situational
        BLUE_ITEM -> -200.0
    }
    return rating - (card.cost * 2 + 1)
}

fun getCardRating(card: Card): Double {
    return when (card.type) {
        CREATURE, GREEN_ITEM -> card.attack + card.defense + (card.myHealthChange / 2) - (card.opponentHealthChange / 2) + (card.cardDraw * 2) + getAbilitiesRating(card)
        RED_ITEM, BLUE_ITEM -> (-card.attack - card.defense + (card.myHealthChange / 2) - (card.opponentHealthChange / 2) + (card.cardDraw * 2)).toDouble() // remove abilities is very situational
    }
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
        rating += card.attack
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
            val abilities = input.next().filter { char -> char != '-' }.map { ability -> Ability.fromCode(ability.toString()) }.toMutableSet()
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
            var bestScore = Double.NEGATIVE_INFINITY
            var setof = mutableSetOf<String>()
            Benchmark.runUntil(TIMEOUT) {
                //val simulation = GameSimulation(state.copy())
                val simulation = GameSimulation(
                        gameState = state.copy(
                                board = state.board.copy(myCards = state.board.myCards.toMutableList(), opponentCards = state.board.opponentCards.toMutableList()),
                                players = listOf(state.me().copy(), state.opponent().copy()),
                                hand = state.hand.toMutableList(),
                                deck = state.deck.toMutableList(),
                                score = Double.NEGATIVE_INFINITY
                        ),
                        actionPlan = ActionPlan(mutableListOf())
                )

                simulation.play()
                simulation.eval()

                //setof.add("score ${simulation.gameState.score} plan ${simulation.actionPlan.log()}")

                if (simulation.gameState.score > bestScore) {
                    bestScore = simulation.gameState.score
                    bestActionPlan = simulation.actionPlan
                }
            }
            actionPlan = bestActionPlan
            setof.forEach { log(it) }
            log("best score is $bestScore")
        }
    }

    fun write() {
        actionPlan.execute()
    }
}

//shufflePlayer0Seed=-4770668948709142815
//seed=2493166087447067600
//draftChoicesSeed=3558409501800307699
//shufflePlayer1Seed=5667960557175409084


class GameSimulation(
        val gameState: State,
        val actionPlan: ActionPlan = ActionPlan(mutableListOf())) {

    fun eval() {

        // draw next turn ?

        if (gameState.opponent().health <= 0) {
            gameState.score = Double.MAX_VALUE
            log("hola")
            return
        }

        // My board
        gameState.score = gameState.board.myCards.sumByDouble { getCardRating(it) }

        // Opponent's board
        gameState.score -= gameState.board.opponentCards.sumByDouble { getCardRating(it) } * 2

        // My health
        gameState.score += gameState.me().health

        // Opponent's health
        gameState.score -= gameState.opponent().health


        // Remove points if mana left
        //gameState.score -= gameState.me().mana

        // My deck size compared to the enemy's
        //gameState.score += gameState.me().deckSize - gameState.opponent().deckSize

        // Nb of cards in hand
        //gameState.score += (gameState.hand.size)
    }

    fun play() {

        // Summon cards and use items
        var hasCardsToSummon = gameState.hand.any { it.cost <= gameState.me().mana && !it.analyzed }
        while (hasCardsToSummon) {
            val card = gameState.hand.filter { it.cost <= gameState.me().mana && !it.analyzed }.getRandomElement()
            when (card) {
                is Creature -> actionPlan.add(summonCreature(card, gameState))
                is RedItem -> {
                    if (gameState.board.opponentCards.size > 0) {
                        actionPlan.add(useRedItem(card, gameState.board.opponentCards.getRandomElement(), gameState))
                    } else {
                        card.analyzed = true
                    }
                }
                is GreenItem -> {
                    if (gameState.board.myCards.size > 0) {
                        actionPlan.add(useGreenItem(card, gameState.board.myCards.getRandomElement(), gameState))
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
                    if (randomTargetIndex < gameState.board.opponentCards.size) {
                        target = gameState.board.opponentCards.getRandomElement()
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
                            target = gameState.board.myCards.getRandomElement()
                        }
                    }
                }
                attack(creature, target, gameState)

                enemyHasCreatureToPlay = gameState.board.opponentCards.any { !it.played && it.attack > 0 }
            }
    }
}

data class State(
        val board: Board = Board(mutableListOf(), mutableListOf()),
        var players: List<Player> = listOf(),
        var hand: MutableList<Card> = mutableListOf(),
        var deck: MutableList<Card> = mutableListOf(),
        var score: Double = Double.NEGATIVE_INFINITY) {

    fun me(): Player {
        return players[0]
    }

    fun opponent(): Player {
        return players[1]
    }

    fun isInDraftPhase(): Boolean {
        return players[0].mana <= 0
    }

    fun copy(): State {
        return State(board.copy(), players.toList(), hand.toMutableList(), deck.toMutableList(), Double.NEGATIVE_INFINITY)
    }
}

data class Board(val myCards: MutableList<Creature>, val opponentCards: MutableList<Creature>) {
    fun copy(): Board {
        return Board(myCards.toMutableList(), opponentCards.toMutableList())
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

abstract class Card(val id: Int, val instanceId: Int, val location: Int, val type: CardType, val cost: Int, var attack: Int, var defense: Int, val abilities: MutableSet<Ability>,
                    val myHealthChange: Int, val opponentHealthChange: Int, val cardDraw: Int, var played: Boolean = false) {
    var analyzed = false

    override fun toString(): String = instanceId.toString()
    fun hasAbilities(): Boolean {
        return abilities.isNotEmpty()
    }
}

abstract class Item(id: Int, instanceId: Int, location: Int, type: CardType, cost: Int, attack: Int, defense: Int, abilities: MutableSet<Ability>, myHealthChange: Int, opponentHealthChange: Int, cardDraw: Int) : Card(id, instanceId, location, type, cost, attack, defense, abilities, myHealthChange, opponentHealthChange, cardDraw)
class Creature(id: Int, instanceId: Int, location: Int, cost: Int, attack: Int, defense: Int, abilities: MutableSet<Ability>, myHealthChange: Int, opponentHealthChange: Int, cardDraw: Int) : Card(id, instanceId, location, CREATURE, cost, attack, defense, abilities, myHealthChange, opponentHealthChange, cardDraw)
class GreenItem(id: Int, instanceId: Int, location: Int, cost: Int, attack: Int, defense: Int, abilities: MutableSet<Ability>, myHealthChange: Int, opponentHealthChange: Int, cardDraw: Int) : Item(id, instanceId, location, GREEN_ITEM, cost, attack, defense, abilities, myHealthChange, opponentHealthChange, cardDraw)
class RedItem(id: Int, instanceId: Int, location: Int, cost: Int, attack: Int, defense: Int, abilities: MutableSet<Ability>, myHealthChange: Int, opponentHealthChange: Int, cardDraw: Int) : Item(id, instanceId, location, RED_ITEM, cost, attack, defense, abilities, myHealthChange, opponentHealthChange, cardDraw)
class BlueItem(id: Int, instanceId: Int, location: Int, cost: Int, attack: Int, defense: Int, abilities: MutableSet<Ability>, myHealthChange: Int, opponentHealthChange: Int, cardDraw: Int) : Item(id, instanceId, location, BLUE_ITEM, cost, attack, defense, abilities, myHealthChange, opponentHealthChange, cardDraw)
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