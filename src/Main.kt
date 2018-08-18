import CardLocation.*
import CardType.*
import java.util.*

const val MAX_CREATURES_ON_BOARD = 6
const val MAX_CARDS_IN_HAND = 8
const val TIMEOUT = 95
val RANDOM = Random()

fun main(args: Array<String>) {
    val bot = Bot()

    while (true) {
        bot.read()
        bot.think()
        bot.write()
    }
}

class Bot {
    private val input = Scanner(System.`in`)
    private var firstInit = true
    private lateinit var state: State
    private lateinit var actionPlan: ActionPlan

    fun read() {
        if (firstInit) {
            state = State()
            firstInit = false
        } else {
            val deck = state.deck.toMutableList()
            state = State()
            state.deck = deck
        }
        var inputString : String  = ""//TODO log all input
        val myHealth = input.nextInt()
        val myMana = input.nextInt()
        val myDeckSize = input.nextInt()
        val myRunes = input.nextInt()
        val enemyHealth = input.nextInt()
        val enemyMana = input.nextInt()
        val enemyDeckSize = input.nextInt()
        val enemyRunes = input.nextInt()
        state.players = arrayOf(
                Player(myHealth, myMana, myDeckSize, myRunes),
                Player(enemyHealth, enemyMana, enemyDeckSize, enemyRunes))

        inputString += "< $myHealth $myMana $myDeckSize $myRunes\n"
        inputString += "< $enemyHealth $enemyMana $enemyDeckSize $enemyRunes\n"

        val opponentHandSize = input.nextInt()
        val cardsInPlayCount = input.nextInt()
        inputString += "< $opponentHandSize $cardsInPlayCount\n"

        for (i in 0 until cardsInPlayCount) {
            val cardNumber = input.nextInt()
            val instanceId = input.nextInt()
            val locationId = input.nextInt()
            val location = CardLocation.values().first { it.id == locationId }
            val cardType = CardType.values()[input.nextInt()]
            val cost = input.nextInt()
            val attack = input.nextInt()
            val defense = input.nextInt()
            val abilities = input.next()
            val myHealthChange = input.nextInt()
            val opponentHealthChange = input.nextInt()
            val cardDraw = input.nextInt()

            inputString += "< $cardNumber $instanceId $locationId $locationId $cardType $cost $attack $defense $abilities $myHealthChange $opponentHealthChange $cardDraw\n"

            var charge = false
            var breakthrough = false
            var drain = false
            var ward = false
            var lethal = false
            var guard = false

            for (j in 0 until abilities.length) {
                when (abilities[j]) {
                    'C' -> charge = true
                    'G' -> guard = true
                    'B' -> breakthrough = true
                    'W' -> ward = true
                    'L' -> lethal = true
                    'D' -> drain = true
                }
            }

            val card = Card(cardNumber, instanceId, location, cardType, cost, attack, defense, breakthrough, lethal, drain, charge, guard, ward, myHealthChange, opponentHealthChange, cardDraw)
            when (card.location) {
                OpponentBoard, MyBoard -> card.canAttack = true
                else -> card.canAttack = false
            }

            state.cards.add(card)
        }
        log("$inputString")
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

    private val repartition = mutableListOf(0, 2, 5, 6, 7, 5, 3, 2)
    private fun cardForMostEfficientCurve(cards: MutableList<Card>, deck: MutableList<Card>): Int {
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

    private fun getDraftCardRating(card: Card): Double {
        val rating: Double = when (card.type) {
            CREATURE, GREEN_ITEM -> card.attack + card.defense + (card.myHealthChange / 2) - (card.opponentHealthChange / 2) + (card.cardDraw * 2) + card.abilitiesRating()
            RED_ITEM -> (-card.attack - card.defense + (card.myHealthChange / 2) - (card.opponentHealthChange / 2) + (card.cardDraw * 2)).toDouble() // remove abilities is very situational
            BLUE_ITEM -> -200.0
        }
        return rating - (card.cost * 2 + 1)
    }
}

//shufflePlayer0Seed=-4770668948709142815
//seed=2493166087447067600
//draftChoicesSeed=3558409501800307699
//shufflePlayer1Seed=5667960557175409084

class GameSimulation(
        val gameState: State,
        val actionPlan: ActionPlan = ActionPlan()) {

    fun play() {
        // Summon cards and use items
        summon()

        // Attack
        attack({ it.location == MyBoard }, { it.location == OpponentBoard }, false)

        // Be attacked
        //attack({it.location == OpponentBoard}, {it.location == MyBoard}, true)
    }

    private fun summon() {
        val cards = gameState.cards
        var hasCardsToSummon = cards.inMyHand().any { it.cost <= gameState.me().mana && !it.analyzed }
        var boardNotFull = cards.count { it.location == MyBoard } < MAX_CREATURES_ON_BOARD

        while (hasCardsToSummon && boardNotFull) {
            val cardIdx = cards.getRandomIndexForPredicate { it.location == MyHand && it.cost <= gameState.me().mana && !it.analyzed }
            val action: Action? = when (cards[cardIdx].type) {
                CREATURE -> Action.summon(gameState, cardIdx)
                RED_ITEM -> {
                    if (cards.onOpponentBoard().isNotEmpty()) {
                        Action.use(gameState, cardIdx, cards.getRandomIndexForPredicate { it.location == OpponentBoard })
                    } else {
                        cards[cardIdx].analyzed = true
                        return
                    }
                }
                GREEN_ITEM -> {
                    if (cards.onMyBoard().isNotEmpty()) {
                        Action.use(gameState, cardIdx, cards.getRandomIndexForPredicate { it.location == MyBoard })
                    } else {
                        cards[cardIdx].analyzed = true
                        return
                    }
                }
                BLUE_ITEM -> {
                    val targetIdx = if (cards.onOpponentBoard().isNotEmpty()) cards.getRandomIndexForPredicate { it.location == OpponentBoard } else null //FIXME Healing potion ?
                    Action.use(gameState, cardIdx, targetIdx)
                }
            }

            if (action != null ) {
                if (action.isUseless(gameState)) {
                    cards[cardIdx].analyzed = true
                } else {
                    gameState.update(action)
                    actionPlan.add(action)
                }
            }

            hasCardsToSummon = cards.inMyHand().any { it.cost <= gameState.me().mana && !it.analyzed }
            boardNotFull = cards.count { it.location == MyBoard } < 6
        }
    }

    private fun attack(attackerPredicate: (Card) -> Boolean, targetPredicate: (Card) -> Boolean, isCounterAttack: Boolean) {
        var attackerHasCreatureToPlay = gameState.cards.filter(attackerPredicate).any { it.canAttack && it.attack > 0 }

        while (attackerHasCreatureToPlay) {
            val attackingCreatureIdx = gameState.cards.getRandomPlayableCreatureForPredicate(attackerPredicate)
            var targetCardIdx: Int? = null
            if (gameState.cards.any(targetPredicate)) {
                val targetCards = gameState.cards.filter(targetPredicate)
                if (targetCards.hasGuards()) {
                    targetCardIdx = gameState.cards.getRandomGuardIndexForPredicate(targetPredicate)
                    log("target guard found: ${gameState.cards[targetCardIdx]}")
                } else {
                    if (RANDOM.nextInt(targetCards.size + 1) != 0) {
                        targetCardIdx = gameState.cards.getRandomIndexForPredicate(targetPredicate)
                    } else {
                        // Target hero
                    }
                }
            }
            val action = Action.attack(gameState, attackingCreatureIdx, targetCardIdx)
            gameState.update(action)
            if (!isCounterAttack) {
                actionPlan.add(action)
            }

            attackerHasCreatureToPlay = gameState.cards.filter(attackerPredicate).any { it.canAttack && it.attack > 0 }
        }
    }

    fun eval() {
        // draw next turn ? => cf. Player.cardDrawn //TODO update it when we play a card that makes us draw smth AND put negative score if drawing those cards would make us have more than the MAX_CARDS_IN_HAND!

        if (gameState.opponent().health <= 0) {
            gameState.score = Double.MAX_VALUE
            return
        } else if (gameState.me().health <= 0) {
            gameState.score = Double.NEGATIVE_INFINITY // TODO simulate best actionplan for opponent
        }

        // My board
        gameState.score = gameState.cards.onMyBoard().sumByDouble { it.rating() } * 2

        // Opponent's board
        gameState.score -= gameState.cards.onOpponentBoard().sumByDouble { it.rating() } * 2

        // My health
        gameState.score += gameState.me().health

        // Opponent's health
        gameState.score -= gameState.opponent().health

        // Remove points if mana left
        gameState.score -= gameState.me().mana

        // My deck size compared to the enemy's
        //gameState.score += gameState.me().deckSize - gameState.opponent().deckSize

        // Nb of cards in hand
        gameState.score += (gameState.cards.inMyHand().size) * 0.5
    }
}

class State(
        val cards: MutableList<Card> = mutableListOf(),
        var players: Array<Player> = arrayOf()) {

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
        val newState = State(cards.toMutableList(), players.copyOf())
        newState.deck = this.deck // TODO: any need to copy the deck for simulation?
        return newState
    }

    fun update(action: Action) {

        when (action) {
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
                            if (!targetCard.ward && targetCard.defense + item.defense <= 0) {
                                targetCard.location = DiscardPile
                            } else if (targetCard.ward && item.ward && targetCard.defense + item.defense <= 0) {
                                targetCard.location = DiscardPile
                            } else if (targetCard.ward) {
                                targetCard.removeAbilitiesFrom(item)
                                targetCard.attack += item.attack
                                if (item.defense < 0) targetCard.ward = false
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
                val attackingCard = cards[action.cardIdx]
                attackingCard.canAttack = false

                val attackingPlayerIdx = if (attackingCard.location == MyBoard) 0 else 1
                val attackingPlayer = players[attackingPlayerIdx]
                val opponentPlayer = players[1 - attackingPlayerIdx]

                if (action.targetIdx == null) {
                    opponentPlayer.health -= attackingCard.attack
                } else {
                    val targetCard = cards[action.targetIdx]

                    // Update target's board
                    applyAttackFor(attackingCard, attackingPlayer, targetCard, opponentPlayer, false)

                    // Update attacker's board
                    applyAttackFor(targetCard, opponentPlayer, attackingCard, attackingPlayer, true)
                }
            }
        }
    }

    private fun applyAttackFor(attackingCard: Card, attackingPlayer: Player, targetCard: Card, targetPlayer: Player, isCounterAttack: Boolean) {
        if (targetCard.ward && attackingCard.attack > 0) {
            targetCard.ward = false
        } else {
            if (attackingCard.lethal || attackingCard.attack >= targetCard.defense) {
                targetCard.location = DiscardPile
                val attackRemainder = attackingCard.attack - targetCard.defense
                if (!isCounterAttack && attackingCard.breakthrough && attackRemainder > 0) { // no need to handle defender's breakthrough (https://github.com/Counterbalance/LegendsOfCodeAndMagic/blob/master/src/main/java/com/codingame/game/engine/GameState.java#L326)
                    targetPlayer.health -= attackRemainder
                }
            } else {
                targetCard.defense -= attackingCard.attack
            }

            if (!isCounterAttack && attackingCard.drain) {
                attackingPlayer.health += attackingCard.attack
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

    fun abilitiesRating(): Double {
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
        if (card.ward) this.ward = true
    }

    fun removeAbilitiesFrom(card: Card) {
        if (card.charge) this.charge = false
        if (card.breakthrough) this.breakthrough = false
        if (card.drain) this.drain = false
        if (card.lethal) this.lethal = false
        if (card.guard) this.guard = false
        if (card.ward) this.ward = false
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

    var cardIdx: Int = -1

    companion object {
        fun summon(state: State, cardIdx: Int): Summon {
            return Summon(state.cards[cardIdx].instanceId, cardIdx)
        }

        fun attack(state: State, attackingCardIdx: Int, targetCardIdx: Int? = -1): Attack {
            val attackerId = state.cards[attackingCardIdx].instanceId
            val targetId = if (targetCardIdx == null) -1 else state.cards[targetCardIdx].instanceId
            return Attack(attackerId, attackingCardIdx, targetId, targetCardIdx)
        }

        fun use(state: State, itemCardIdx: Int, targetCardIdx: Int?): Use {
            val itemId = state.cards[itemCardIdx].instanceId
            val targetId = if (targetCardIdx == null) -1 else state.cards[targetCardIdx].instanceId
            return Use(itemId, itemCardIdx, targetId, targetCardIdx)
        }
    }

    open fun isUseless(state: State): Boolean {
        return false
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

class Attack(private val attackerId: Int, itemIdx: Int, private var opponentId: Int = -1, val targetIdx: Int?) : Action() {
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

    override fun isUseless(state: State): Boolean {
        val item = state.cards[cardIdx]

        if (targetIdx == null) return false

        val targetCard = state.cards[targetIdx]
        when (item.type) {
            GREEN_ITEM -> {
                if (item.defense == 0 && item.attack == 0 && item.cardDraw == 0 && item.myHealthChange <= 0) {
                    if (item.guard && !targetCard.guard) return false
                    if (item.drain && !targetCard.drain) return false
                    if (item.breakthrough && !targetCard.breakthrough) return false
                    if (item.lethal && !targetCard.lethal) return false
                    if (item.charge && !targetCard.charge) return false
                    if (item.ward && !targetCard.ward) return false

                    return true
                }
            }
            RED_ITEM -> {
                if (item.defense == 0 && (item.attack == 0 || targetCard.attack == 0) && item.cardDraw == 0 && item.opponentHealthChange >= 0) {
                    if (item.guard && targetCard.guard) return false
                    if (item.drain && targetCard.drain) return false
                    if (item.breakthrough && targetCard.breakthrough) return false
                    if (item.lethal && targetCard.lethal) return false
                    if (item.charge && targetCard.charge) return false
                    if (item.ward && targetCard.ward) return false

                    return true
                }
            }
        }

        return false
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

fun <E> List<E>.getRandomElement() = this[RANDOM.nextInt(this.size)]
fun <E> List<E>.getRandomIndex() = RANDOM.nextInt(this.size)
fun <E : Card> List<Card>.getRandomCardIndexFromMyBoard(): Int {
    return this.getRandomIndexForPredicate { it.location == MyBoard }
}

fun <Card> List<Card>.getRandomIndexForPredicate(predicate: (Card) -> Boolean): Int {
    val indexes = mutableListOf<Int>()
    for ((idx, card) in this.withIndex()) {
        if (predicate(card)) {
            indexes.add(idx)
        }
    }
    return indexes[RANDOM.nextInt(indexes.size)]
}

private fun <E : Card> List<E>.getRandomGuardIndexForPredicate(predicate: (E) -> Boolean): Int {
    val indexes = mutableListOf<Int>()
    for ((idx, card) in this.withIndex()) {
        if (predicate(card) && card.guard) {
            indexes.add(idx)
        }
    }
    return indexes[RANDOM.nextInt(indexes.size)]
}

private fun <E : Card> List<E>.getRandomPlayableCreatureForPredicate(predicate: (E) -> Boolean): Int {
    val indexes = this.withIndex()
            .filter { (idx, card) -> predicate(card) && card.canAttack && card.attack > 0 }
            .map { (idx, card) -> idx }
    return indexes[RANDOM.nextInt(indexes.size)]
}

fun <T : Card> List<T>.hasGuards(): Boolean = this.any { it.guard }
fun <T : Card> List<T>.guards(): List<T> = this.filter { it.guard }
fun <T : Card> List<T>.inMyHand(): List<T> = this.filter { it.location == MyHand }
fun <T : Card> List<T>.onMyBoard(): List<T> = this.filter { it.location == MyBoard }
fun <T : Card> List<T>.onOpponentBoard(): List<T> = this.filter { it.location == OpponentBoard }

fun log(text: String) {
    System.err.println(text)
}
