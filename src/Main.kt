import Ability.*
import CardType.*
import java.util.*

/**
 * Auto-generated code below aims at helping you parse
 * the standard input according to the problem statement.
 **/
fun main(args: Array<String>) {
    val input = Scanner(System.`in`)
    val game = initGame()
    val actionPlan = ActionPlan(mutableListOf())

    // game loop
    while (true) {
        game.nextRound()
        actionPlan.reinit()
        val gameState = updateGameState(game.state, input)

        Benchmark.logTime("Total play") {
            val deck = gameState.deck

            if (game.isInDraftPhase()) { // DRAFT

                val firstCard = gameState.hand[0]
                val secondCard = gameState.hand[1]
                val thirdCard = gameState.hand[2]
                val bestCardIndex = searchMostRatedCard(firstCard, secondCard, thirdCard, deck)
                deck.add(gameState.hand[bestCardIndex])
                actionPlan.add(Pick(bestCardIndex))

                actionPlan.execute()
            } else { // FIGHT
                val nbOfSimulation = 30000
                val simulations = mutableListOf<GameSimulation>()
                Benchmark.logTime("Simulation time") {
                    for (i in 0..nbOfSimulation) {
                        val simulation = GameSimulation(gameState.copy())
                        playSimulation(simulation)
                        simulation.eval()
                        simulations.add(simulation)
                    }
                }

                val bestSimulation = simulations.sortedByDescending { it.score }.first()
                bestSimulation.actionPlan.execute()
            }
        }
    }
}

fun playSimulation(simulation: GameSimulation) {

    val gameState = simulation.gameState
    val actionPlan = simulation.actionPlan

    // Summon cards and use items
    var hasCardsToSummon = gameState.hand.any { it.cost <= gameState.me().mana }
    while (hasCardsToSummon) {
        val card = gameState.hand.filter { it.cost <= gameState.me().mana }.getRandomElement()
        when (card) {
            is Creature -> actionPlan.add(summonCreature(card, gameState))
            is RedItem -> {
                val targetId = if (gameState.board.opponentCards.size > 0) gameState.board.opponentCards.getRandomElement().instanceId else -1
                actionPlan.add(useItem(card, gameState, targetId))
            }
            is GreenItem -> {
                val targetId = if (gameState.board.myCards.size > 0) gameState.board.myCards.getRandomElement().instanceId else -1
                actionPlan.add(useItem(card, gameState, targetId))
            }
            is BlueItem -> {
                val targetId = if (gameState.board.myCards.size > 0) gameState.board.myCards.getRandomElement().instanceId else -1
                actionPlan.add(useItem(card, gameState, targetId))
            }
        }
        hasCardsToSummon = gameState.hand.any { it.cost <= gameState.me().mana }
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
}

fun useItem(item: Item, gameState: State, targetId: Int = -1): Action {
    gameState.me().mana -= item.cost
    gameState.hand.remove(item)
    return Use(item.instanceId, targetId)
}

fun attack(attacker: Creature, target: Creature?, gameState: State): Action {
    attacker.played = true

    if (target != null) {
        if (attacksKillTarget(attacker, target)) {
            System.err.println("Removing $target from board")
            gameState.board.opponentCards.remove(target)
        } else if (target.abilities.contains(Ability.WARD)) {
            System.err.println("Removing WARD from $target")
            gameState.board.opponentCards.find { card -> card.id == target.id }?.abilities?.remove(WARD)
        } else if (!target.abilities.contains(Ability.WARD)) {
            System.err.println("Reducing life of $target")
            gameState.board.opponentCards.first { card -> card.id == target.id }.defense -= attacker.attack
        }
    }

    return Attack(attacker.instanceId, target?.instanceId ?: -1)
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
            0 -> gameState.hand.add(card)
            1 -> gameState.board.myCards.add(card as Creature)
            -1 -> gameState.board.opponentCards.add(card as Creature)
        }
    }

    return gameState
}

fun searchMostRatedCard(firstCard: Card, secondCard: Card, thirdCard: Card, deck: MutableList<Card>): Int {
    return arrayListOf(firstCard, secondCard, thirdCard).map { getCardRating(it) }.indexOfMax()
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
class Game(private var round: Int, val state: State) {
    fun nextRound() {
        round++
    }

    fun isInDraftPhase(): Boolean {
        return round <= 30
    }
}

class GameSimulation(val gameState: State) {
    val actionPlan: ActionPlan = ActionPlan(mutableListOf())
    var score: Double = 0.0

    fun eval() {
        this.score = 0.0

        // My cards
        score += gameState.board.myCards.sumByDouble { getCardRating(it) }

        // Opponent's cards
        score -= gameState.board.opponentCards.sumByDouble { getCardRating(it) }

        // My health
        score += gameState.me().health

        // Opponent's health
        score -= gameState.opponent().health

        // My maximum expected life at next turn
        score -= gameState.board.opponentCards.sumBy { it.attack }

        // My deck size compared to the enemy's
        score += gameState.me().deckSize - gameState.opponent().deckSize
    }
}

data class State(val board: Board, private var players: List<Player>, val hand: MutableList<Card>, val deck: MutableList<Card>) {
    fun me(): Player {
        return players[0]
    }

    fun opponent(): Player {
        return players[1]
    }
}

data class Board(val myCards: MutableList<Creature>, val opponentCards: MutableList<Creature>) {
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

abstract class Card(val id: Int, val instanceId: Int, val location: Int, val type: CardType, val cost: Int, var attack: Int, var defense: Int, val abilities: MutableList<Ability>,
                    val myHealthChange: Int, val opponentHealthChange: Int, val cardDraw: Int, var played: Boolean = false) {
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
    fun execute() {
        if (actions.isEmpty()) {
            add(Pass())
        }
        println(actions.joinToString(";"))
    }

    fun add(action: Action) {
        actions.add(action)
    }

    fun reinit() {
        actions.clear()
    }

    fun addAll(newActions: List<Action>) {
        actions.addAll(newActions)
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
fun <T : Creature> List<T>.hasGuards() : Boolean = this.any { it.abilities.contains(GUARD) }
fun <T : Creature> List<T>.guards() : List<T> = this.filter{ it.abilities.contains(GUARD) }

fun log(text: String) {
    System.err.println(text)
}