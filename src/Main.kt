import Ability.*
import CardLocation.*
import CardType.*
import java.util.*

const val TIMEOUT = 95

fun main(args: Array<String>) {
    val bot = Bot()

    while (true) {
        bot.read()
        bot.think()
        bot.write()
    }
}

fun attack(attacker: Card, target: Card?, gameState: State): Action {
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

val repartition = mutableListOf(0, 2, 5, 6, 7, 5, 3, 2)
fun cardForMostEfficientCurve(cards: MutableList<Card>, deck: MutableList<Card>): Int {
    val needItem = cards.filter { card -> card.type != CREATURE }.size < deck.size / 5
    val firstCard = cards.inMyHand()[0]
    val secondCard = cards.inMyHand()[1]
    val thirdCard = cards.inMyHand()[2]
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

/*****************************************************************************************************
 ******************************************   MODELS **************************************************
 ******************************************************************************************************/

class Bot {
    private val input = Scanner(System.`in`)
    private var firstInit = true
    private lateinit var state: State
    private lateinit var actionPlan: ActionPlan

    fun read() {
        if (firstInit) {
            state = State()
        } else {
            firstInit = false
            val deck  = state.deck.toMutableList()
            state = State()
            state.deck = deck
        }

        state.players = listOf(
                Player(input.nextInt(), input.nextInt(), input.nextInt(), input.nextInt()),
                Player(input.nextInt(), input.nextInt(), input.nextInt(), input.nextInt()))

        val opponentHandSize = input.nextInt()
        val cardsInPlayCount = input.nextInt()
        for (i in 0 until cardsInPlayCount) {
            val cardNumber = input.nextInt()
            val instanceId = input.nextInt()
            val location = CardLocation.values().first { it.id == input.nextInt() }
            val cardType = CardType.values()[input.nextInt()]
            val cost = input.nextInt()
            val attack = input.nextInt()
            val defense = input.nextInt()
            val abilities = input.next()
            val myHealthChange = input.nextInt()
            val opponentHealthChange = input.nextInt()
            val cardDraw = input.nextInt()
            var charge = false
            var breakthrough = false
            var drain = false
            var ward = false
            var lethal = false
            var guard = false

            for (i in 0..abilities.length) {
                when(abilities[i]) {
                    'C' -> charge = true
                    'G' -> guard = true
                    'B' -> breakthrough = true
                    'W' -> ward = true
                    'L' -> lethal = true
                    'D' -> drain = true
                }
            }

            val card = Card(cardNumber, instanceId, location, cardType, cost, attack, defense, breakthrough, lethal, drain, charge, guard, ward, myHealthChange, opponentHealthChange, cardDraw)
            when(card.location) {
                OpponentBoard, MyBoard -> card.canAttack = true
                else -> card.canAttack = false
            }

            state.cards.add(card)
        }
    }

    fun think() {
        actionPlan = ActionPlan()

        if (state.isInDraftPhase()) {
            //val bestCardIndex = searchMostRatedCard(firstCard, secondCard, thirdCard, deck)
            val efficientIndexCard = cardForMostEfficientCurve(state.cards, state.deck)
            state.deck.add(state.cards[efficientIndexCard])
            actionPlan.add(Pick(efficientIndexCard))
        } else {
            var bestActionPlan = actionPlan
            var bestScore = Double.NEGATIVE_INFINITY

            Benchmark.runUntil(TIMEOUT) {
                val simulation = GameSimulation(state.copy(), ActionPlan())
                simulation.play()
                simulation.eval()

                if (simulation.gameState.score > bestScore) {
                    bestScore = simulation.gameState.score
                    bestActionPlan = simulation.actionPlan
                }
            }
            actionPlan = bestActionPlan
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
        val actionPlan: ActionPlan = ActionPlan()) {

    fun eval() {

        // draw next turn ? => cf. Player.cardDrawn //TODO update it when we play a card that makes us draw smth

        if (gameState.opponent().health <= 0) {
            gameState.score = Double.MAX_VALUE
            log("hola")
            return
        }

        // My board
        gameState.score = gameState.cards.onMyBoard().sumByDouble { it.rating() }

        // Opponent's board
        gameState.score -= gameState.cards.onOpponentBoard().sumByDouble { it.rating() } * 2

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
        summon()

        // Attack
        attack(attackerCards = gameState.cards.onMyBoard(), targetCards = gameState.cards.onOpponentBoard())

        // Be attacked
        attack(attackerCards = gameState.cards.onOpponentBoard(), targetCards = gameState.cards.onMyBoard())
    }

    private fun summon() {
        val cards = gameState.cards
        var hasCardsToSummon = cards.inMyHand().any { it.cost <= gameState.me().mana && !it.analyzed }

        while (hasCardsToSummon) {
            val cardIdx = cards.inMyHand().filter { it.cost <= gameState.me().mana && !it.analyzed }.getRandomIndex()
            val action: Action? = when (cards[cardIdx].type) {
                CREATURE -> Action.summon(gameState, cardIdx)
                RED_ITEM -> {
                    if (cards.onOpponentBoard().isNotEmpty()) {
                        Action.use(gameState, cardIdx, cards.onOpponentBoard().getRandomIndex())
                    } else {
                        cards[cardIdx].analyzed = true
                        return
                    }
                }
                GREEN_ITEM -> {
                    if (cards.onMyBoard().isNotEmpty()) {
                        Action.use(gameState, cardIdx, cards.onMyBoard().getRandomIndex())
                    } else {
                        cards[cardIdx].analyzed = true
                        return
                    }
                }
                BLUE_ITEM -> {
                    val targetIdx = if (cards.onOpponentBoard().isNotEmpty()) cards.onOpponentBoard().getRandomIndex() else null
                    Action.use(gameState, cardIdx, targetIdx)
                }
            }

            action?.let {
                gameState.update(action)
                actionPlan.add(action)
            }

            hasCardsToSummon = cards.inMyHand().any { it.cost <= gameState.me().mana && !it.analyzed }
        }
    }

    private fun attack(attackerCards: List<Card>, targetCards: List<Card>) {
        var attackerHasCreatureToPlay = attackerCards.any { !it.played && it.attack > 0 }
        while (attackerHasCreatureToPlay) {
            val attackingCreature = attackerCards.filter { !it.played && it.attack > 0 }.getRandomElement()
            var target: Card? = null
            if (targetCards.isNotEmpty()) {
                if (targetCards.hasGuards()) {
                    target = targetCards.guards().getRandomElement()
                } else {
                    // Fetching random int from 0 to size + 1 to have an index also for enemy hero
                    // Then Int which is out of bound represents the enemy hero
                    val randomTargetIndex = Random().nextInt(targetCards.size + 1)
                    if (randomTargetIndex < targetCards.size) {
                        target = targetCards.getRandomElement()
                    }
                }
            }
            actionPlan.add(attack(attackingCreature, target, gameState))

            attackerHasCreatureToPlay = attackerCards.any { !it.played && it.attack > 0 }
        }
    }
}

class State(
        val cards: MutableList<Card> = mutableListOf(),
        var players: List<Player> = listOf()) {

    var score: Double = Double.NEGATIVE_INFINITY
    var deck: MutableList<Card> = mutableListOf()

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
        val newState= State(cards.toMutableList(), players.toList())
        newState.deck = this.deck // TODO: any need to copy the deck for simulation?
        return newState
    }

    fun update(action: Action) {

        when(action) {
            is Summon -> {
                val card = cards[action.cardIdx]

                card.canAttack = card.charge
                me().mana -= card.cost
                me().health += card.myHealthChange
                opponent().health += card.opponentHealthChange
                me().cardsDrawn += card.cardDraw
                card.location = MyBoard
            }
            is Use -> {
                val item = cards[action.cardIdx]
                me().mana -= item.cost
                item.location = DiscardPile
                when (item.type) {
                    GREEN_ITEM -> {
                        if (action.targetIdx == null) throw Exception("Green item should always have a target")

                        me().health += item.myHealthChange
                        me().cardsDrawn += item.cardDraw

                        val targetCard = cards[action.targetIdx]
                        targetCard.attack += item.attack
                        targetCard.defense += item.defense
                        targetCard.addAbilitiesFrom(item)
                    }
                    RED_ITEM, BLUE_ITEM -> {
                        if (action.targetIdx == null && item.type == RED_ITEM) throw Exception("Red item should always have a target")

                        me().health += item.myHealthChange
                        me().cardsDrawn += item.cardDraw
                        opponent().health += item.opponentHealthChange
                        if (action.targetIdx != null) {
                            val targetCard = cards[action.targetIdx]
                            if (!targetCard.ward && targetCard.defense <= item.defense) {
                                targetCard.location = DiscardPile
                            } else if (targetCard.ward && item.ward && targetCard.defense <= item.defense) {
                                targetCard.location = DiscardPile
                            } else if (targetCard.ward) {
                                targetCard.removeAbilitiesFrom(item)
                                targetCard.attack += item.attack
                                if (item.defense > 0) targetCard.ward = false
                            } else if (!targetCard.ward) {
                                targetCard.removeAbilitiesFrom(item)
                                targetCard.defense += item.defense
                                targetCard.attack += item.attack
                            }
                        }
                    }
                    else -> throw Exception("Shouldn't have type ${item.type} for an action of type Use")
                }
                item.location = DiscardPile
            }
            is Attack -> {
                //TODO finish this
            }
        }
    }
}

data class Player(var health: Int,
                  var mana: Int,
                  var deckSize: Int,
                  var runes: Int,
                  var cardsDrawn: Int = 0)
class Card(val id: Int,
            val instanceId: Int,
            var location: CardLocation,
            val type: CardType,
            val cost: Int,
            var attack: Int,
            var defense: Int,
            var breakthrough: Boolean,
            var lethal: Boolean,
            var drain: Boolean,
            var charge: Boolean,
            var guard: Boolean,
            var ward: Boolean,
            val myHealthChange: Int,
            val opponentHealthChange: Int,
            val cardDraw: Int,
            var canAttack: Boolean = true) {
    var analyzed = false

    override fun toString(): String = instanceId.toString()

    fun rating(): Double {
        return when (type) {
            CREATURE, GREEN_ITEM -> attack + defense + (myHealthChange / 2) - (opponentHealthChange / 2) + (cardDraw * 2) + abilitiesRating()
            RED_ITEM, BLUE_ITEM -> (-attack - defense + (myHealthChange / 2) - (opponentHealthChange / 2) + (cardDraw * 2)).toDouble() // remove abilities is very situational
        }
    }

    private fun abilitiesRating(): Double {
        var rating = 0.0
        if (breakthrough) {
            rating += 1
        }
        if (charge) {
            rating += 2
        }
        if (drain) {
            rating += 0.5 * attack
        }
        if (guard) {
            rating += 1
        }
        if (lethal) {
            rating += 3
        }
        if (ward) {
            rating += attack
        }
        return rating
    }

    // TODO: maybe rework how abilities are stored to avoid doing this. bitmask?
    fun addAbilitiesFrom(card: Card) {
        if (card.charge) this.charge = true
        if (card.breakthrough) this.breakthrough = true
        if (card.drain) this.drain = true
        if (card.lethal) this.lethal = true
        if (card.guard) this.guard = true
        if (card.ward) this.ward= true
    }

    fun removeAbilitiesFrom(card: Card) {
        if (card.charge) this.charge = false
        if (card.breakthrough) this.breakthrough = false
        if (card.drain) this.drain = false
        if (card.lethal) this.lethal = false
        if (card.guard) this.guard = false
        if (card.ward) this.ward= false
    }
}

enum class CardLocation(val id: Int) {
    OpponentBoard(-1), MyHand(0), MyBoard(1), DiscardPile(2)
}

enum class Ability(val code: String) {
    CHARGE("C"), BREAKTHROUGH("B"), GUARD("G"), DRAIN("D"), LETHAL("L"), WARD("W");
}

enum class CardType {
    CREATURE, GREEN_ITEM, RED_ITEM, BLUE_ITEM
}

class ActionPlan(private var actions: MutableList<Action> = mutableListOf()) {

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

abstract class Action {
    var cardIdx: Int  = -1
    companion object {
        fun summon(state: State, cardIdx: Int) : Summon {
            return Summon(state.cards[cardIdx].instanceId, cardIdx)
        }
        fun attack(state: State, attackingCardIdx: Int, targetCardIdx: Int? = -1) : Attack {
            val attackerId = state.cards[attackingCardIdx].instanceId
            val targetId = if (targetCardIdx == null) -1 else state.cards[targetCardIdx].instanceId
            return Attack(attackerId, attackingCardIdx, targetId, targetCardIdx)
        }
        fun use(state: State, itemCardIdx: Int, targetCardIdx: Int?) : Use {
            val itemId = state.cards[itemCardIdx].instanceId
            val targetId = if (targetCardIdx == null) -1 else state.cards[targetCardIdx].instanceId
            return Use(itemId, itemCardIdx, targetId, targetCardIdx)
        }
    }
}

class Pick(private val cardId: Int) : Action() {

    override fun toString(): String {
        return "PICK $cardId"
    }
}

class Summon(private val instanceId: Int, creatureIdx: Int) : Action() {
    init {
        this.cardIdx = creatureIdx
    }
    override fun toString(): String {
        return "SUMMON $instanceId"
    }
}

class Pass : Action() {
    override fun toString(): String {
        return "PASS"
    }
}

class Attack(private val attackerId: Int,  itemIdx: Int, private var opponentId: Int = -1, val targetIdx: Int?) : Action() {
    init {
        this.cardIdx = itemIdx
    }
    override fun toString(): String {
        return "ATTACK $attackerId $opponentId"
    }
}

class Use(private val itemId: Int, itemIdx: Int, private val targetId: Int = -1, val targetIdx: Int?) : Action() {
    init {
        this.cardIdx = itemIdx
    }
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
fun <E> List<E>.getRandomIndex() = Random().nextInt(this.size)
fun <T : Card> List<T>.hasGuards(): Boolean = this.any { it.guard }
fun <T : Card> List<T>.guards(): List<T> = this.filter { it.guard }
fun <T : Card> List<T>.inMyHand(): List<T> = this.filter { it.location == CardLocation.MyHand }
fun <T : Card> List<T>.onMyBoard(): List<T> = this.filter { it.location == MyBoard }
fun <T : Card> List<T>.onOpponentBoard(): List<T> = this.filter { it.location == OpponentBoard }

fun log(text: String) {
    System.err.println(text)
}