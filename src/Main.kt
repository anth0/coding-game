
import Ability.*
import CardType.*
import java.util.*

const val MAX_MANA = 12

//control deck
//val repartition = mutableListOf(0, 2, 5, 6, 7, 5, 3, 2)

//aggro deck
val repartition = mutableListOf(2, 7, 6, 7, 4, 2, 1, 1)

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

                gameState.board.opponentCards
                        .filter { card -> card.abilities.contains(GUARD) }
                        .forEach { card -> searchBestTrade(card, gameState, commands) }

                val opponentGuard = gameState.board.opponentCards.firstOrNull { card -> card.abilities.contains(GUARD) }

                if (opponentGuard == null) {
                    //dangerous card
                    gameState.board.opponentCards
                            .filter { card -> card.abilities.contains(DRAIN) }
                            .forEach { card -> searchBestTrade(card, gameState, commands) }

                    gameState.board.opponentCards
                            .sortedByDescending { card -> card.cost }
                            .forEach { card -> searchBestTrade(card, gameState, commands) }
                }

                while (gameState.hand
                                .filter { card -> card.cost <= gameState.me().mana && card.type == CREATURE }
                                .sortedByDescending { card -> card.cost }
                                .isNotEmpty() && gameState.me().mana >= 0) {
                    val cardToPlay: Card? = gameState.hand
                            .filter { card -> card.cost <= gameState.me().mana && card.type == CREATURE }
                            .sortedByDescending { card -> card.cost }
                            .firstOrNull()
                    if (cardToPlay != null) {
                        commands.add(summonCreature(cardToPlay as Creature, gameState))

                        //TODO useless in this phase (ITEM card are situational card, usefull during searchBestTrade)
//                            GREEN_ITEM ->  {
//                                val command = useGreenItem(cardToPlay, gameState)
//                                if (command != null) {
//                                    commands.add(command)
//                                }
//                            }
//                            RED_ITEM -> {
//                                val command = useRedItem(cardToPlay, gameState)
//                                if (command != null) {
//                                    commands.add(command)
//                                }
//                            }
//                            BLUE_ITEM -> commands.add(useItem(cardToPlay, gameState))
//                        }
                    }
                }

                //TODO attack more intelligently by making better trades
                gameState.board.myCards
                        .filter { card -> !card.played && card.attack > 0 }
                        .sortedBy { card -> card.cost }
                        .forEach { card -> commands.add(attackWithCreatureInBoard(card, opponentGuard?.instanceId ?: -1)) }
            }
        }

        commands.execute()
    }
}

fun searchBestTrade(enemyToTrade: Card, gameState: State, commands: Commands) {
    val findATrade: Boolean = if (enemyToTrade.abilities.contains(WARD)) {
        searchBestTradeVersusWardCreature(gameState, enemyToTrade, commands)
    } else {
        searchBestTradeVersusNormalCreature(gameState, enemyToTrade, commands)
    }
    if (findATrade) {
        gameState.board.opponentCards.remove(enemyToTrade)
    }
}

fun searchBestTradeVersusWardCreature(gameState: State, enemyToTrade: Card, commands: Commands): Boolean {
    return when {
        findAndUseRedItemAbleToKillEnemyCreature(gameState, enemyToTrade, commands) -> true
        findAndAttackWithTwoCreatureAbleToKillEnnemyCreature(gameState, enemyToTrade, commands) -> true
        else -> false
    }
}

fun findAndAttackWithTwoCreatureAbleToKillEnnemyCreature(gameState: State, enemyToTrade: Card, commands: Commands): Boolean {
    var bestTrade: Card? = null
    var antiWard: Card? = null
    for (creature in gameState.board.myCards.filter { card -> !card.played && card.attack >= 1 && (card.defense > enemyToTrade.attack || card.abilities.contains(WARD)) }.sortedBy { card -> card.attack }) {
        bestTrade = gameState.board.myCards
                .filter { card ->
                    !card.played &&
                            card.instanceId != creature.instanceId &&
                            card.attack >= enemyToTrade.defense &&
                            (card.defense > enemyToTrade.attack || card.abilities.contains(WARD))
                }
                .sortedBy { card -> card.cost }
                .firstOrNull()
        if (bestTrade != null) {
            antiWard = creature
            break
        }
    }
    return if (bestTrade != null && antiWard != null) {
        commands.add(attackWithCreatureInBoard(antiWard as Creature, enemyToTrade.instanceId))
        commands.add(attackWithCreatureInBoard(bestTrade as Creature, enemyToTrade.instanceId))
        true
    } else {
        false
    }
}

fun findAndUseRedItemAbleToKillEnemyCreature(gameState: State, enemyToTrade: Card, commands: Commands): Boolean {
    val bestTrade = gameState.hand
            .filter { card ->
                card.cost <= gameState.me().mana &&
                        card.type == RED_ITEM &&
                        card.abilities.contains(WARD) &&
                        -card.defense >= enemyToTrade.defense
            }
            .sortedBy { card -> card.cost }
            .firstOrNull()

    return if (bestTrade != null) {
        commands.add(useItem(bestTrade, gameState, enemyToTrade.instanceId))
        true
    } else {
        false
    }
}

fun searchBestTradeVersusNormalCreature(gameState: State, enemyToTrade: Card, commands: Commands): Boolean {
    return when {
        findAndUseItemCostEffectiveAbleToKillCreature(gameState, enemyToTrade, commands) -> true
        findAndAttackWithCreatureCostEffectiveAbleToKillEnnemyCreature(gameState, enemyToTrade, commands) -> true
        findAndAttackWithWardCreatureAbleToKillEnemyCreature(gameState, enemyToTrade, commands) -> true
        findAndAttackWithChargeCreatureAbleToKillEnemyCreature(gameState, enemyToTrade, commands) -> true
        findAndUseBuffOnCreatureAbleToKillEnemyCreature(gameState, enemyToTrade, commands) -> true
        findAndUseItemAbleToKillEnemyCreature(gameState, enemyToTrade, commands) -> true
        findAndAttackWithCreatureAbleToKillEnemyCreature(gameState, enemyToTrade, commands) -> true
        findAndSuicideWithCreatureCostEffectiveAbleToKillEnnemy(gameState, enemyToTrade, commands) -> true
        else -> false
    }
}

fun findAndSuicideWithCreatureCostEffectiveAbleToKillEnnemy(gameState: State, enemyToTrade: Card, commands: Commands): Boolean {
    val bestTrade = gameState.board.myCards
            .filter { card ->
                !card.played &&
                        card.cost <= enemyToTrade.cost &&
                        card.attack >= enemyToTrade.defense
            }
            .sortedBy { card -> card.cost }
            .firstOrNull()

    return if (bestTrade != null) {
        commands.add(attackWithCreatureInBoard(bestTrade, enemyToTrade.instanceId))
        true
    } else {
        false
    }
}

fun findAndAttackWithCreatureAbleToKillEnemyCreature(gameState: State, enemyToTrade: Card, commands: Commands): Boolean {
    val bestTrade = gameState.board.myCards
            .filter { card ->
                !card.played &&
                        card.cost > enemyToTrade.cost &&
                        card.attack >= enemyToTrade.defense
            }
            .sortedBy { card -> card.cost }
            .firstOrNull()

    return if (bestTrade != null) {
        commands.add(attackWithCreatureInBoard(bestTrade, enemyToTrade.instanceId))
        true
    } else {
        false
    }
}

fun findAndUseItemAbleToKillEnemyCreature(gameState: State, enemyToTrade: Card, commands: Commands): Boolean {
    val bestTrade = gameState.hand
            .filter { card ->
                card.cost <= gameState.me().mana &&
                        (card.type == BLUE_ITEM || card.type == RED_ITEM) &&
                        -card.defense >= enemyToTrade.defense &&
                        card.cost > enemyToTrade.cost
            }
            .sortedBy { card -> card.cost }
            .firstOrNull()

    return if (bestTrade != null) {
        commands.add(useItem(bestTrade, gameState, enemyToTrade.instanceId))
        true
    } else {
        false
    }
}

fun findAndUseBuffOnCreatureAbleToKillEnemyCreature(gameState: State, enemyToTrade: Card, commands: Commands): Boolean {
    if (gameState.board.myCards.isNotEmpty()) {
        var bestTrade: Card? = null
        var buff: Card? = null
        for (creature in gameState.board.myCards.filter { creature -> !creature.played }) {
            buff = gameState.hand
                    .filter { card ->
                        card.cost <= gameState.me().mana &&
                                card.type == GREEN_ITEM &&
                                card.attack + creature.attack >= enemyToTrade.defense &&
                                (card.defense + creature.defense > enemyToTrade.attack || card.abilities.contains(WARD) || creature.abilities.contains(WARD))
                    }
                    .sortedBy { card -> card.cost }
                    .firstOrNull()
            if (buff != null) {
                bestTrade = creature
                break
            }
        }

        if (bestTrade != null && buff != null) {
            commands.add(useItem(buff, gameState, bestTrade.instanceId))
            commands.add(attackWithCreatureInBoard(bestTrade as Creature, enemyToTrade.instanceId))
            return true
        }
    }
    return false
}

fun findAndAttackWithChargeCreatureAbleToKillEnemyCreature(gameState: State, enemyToTrade: Card, commands: Commands): Boolean {
    val bestTrade = gameState.hand
            .filter { card ->
                card.cost <= gameState.me().mana &&
                        card.type == CREATURE &&
                        card.abilities.contains(CHARGE) &&
                        card.attack >= enemyToTrade.defense &&
                        (card.defense > enemyToTrade.attack || card.abilities.contains(WARD))
            }
            .sortedBy { card -> card.cost }
            .firstOrNull()
    return if (bestTrade != null) {
        commands.add(summonCreature(bestTrade as Creature, gameState))
        commands.add(attackWithCreatureInBoard(bestTrade, enemyToTrade.instanceId))
        true
    } else {
        false
    }

}

fun findAndAttackWithWardCreatureAbleToKillEnemyCreature(gameState: State, enemyToTrade: Card, commands: Commands): Boolean {
    val bestTrade = gameState.board.myCards
            .filter { card ->
                !card.played &&
                        card.abilities.contains(WARD) &&
                        card.attack >= enemyToTrade.defense
            }
            .sortedBy { card -> card.cost }
            .firstOrNull()
    return if (bestTrade != null) {
        commands.add(attackWithCreatureInBoard(bestTrade, enemyToTrade.instanceId))
        true
    } else {
        false
    }
}

fun findAndAttackWithCreatureCostEffectiveAbleToKillEnnemyCreature(gameState: State, enemyToTrade: Card, commands: Commands): Boolean {
    val bestTrade = gameState.board.myCards
            .filter { card ->
                !card.played &&
                        card.cost <= enemyToTrade.cost &&
                        card.attack >= enemyToTrade.defense &&
                        (card.defense > enemyToTrade.attack || card.abilities.contains(WARD))
            }
            .sortedBy { card -> card.cost }
            .firstOrNull()
    return if (bestTrade != null) {
        commands.add(attackWithCreatureInBoard(bestTrade, enemyToTrade.instanceId))
        true
    } else {
        false
    }
}

fun findAndUseItemCostEffectiveAbleToKillCreature(gameState: State, enemyToTrade: Card, commands: Commands): Boolean {
    val bestTrade = gameState.hand
            .filter { card ->
                card.cost <= gameState.me().mana &&
                        (card.type == BLUE_ITEM || card.type == RED_ITEM) &&
                        -card.defense >= enemyToTrade.defense &&
                        card.cost <= enemyToTrade.cost
            }
            .sortedBy { card -> card.cost }
            .firstOrNull()
    return if (bestTrade != null) {
        commands.add(useItem(bestTrade, gameState, enemyToTrade.instanceId))
        true
    } else {
        false
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
                        CREATURE -> commands.add(summonCreature(cardToPlay as Creature, gameState))
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

fun attackWithCreatureInBoard(cardToPlay: Creature, targetId: Int = -1): Command {
    cardToPlay.played = true
    return Attack(cardToPlay.instanceId, targetId)
}

fun summonCreature(cardToPlay: Creature, gameState: State): Command {
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
            1 -> gameState.board.myCards.add(card as Creature)
            -1 -> gameState.board.opponentCards.add(card as Creature)
        }
    }

    return gameState
}

fun searchEfficientCurve(firstCard: Card, secondCard: Card, thirdCard: Card): Int {

    val bestEffectiveCard = arrayOf(firstCard, secondCard, thirdCard)
//            .filter { card -> card.type == CREATURE }
            .maxBy { card -> repartition[Math.min(card.cost, 7)] + getCardRating(card)}

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
    val rating: Double = when (card.type) {
        CREATURE, GREEN_ITEM -> card.attack + card.defense  + (card.myHealthChange / 2) - (card.opponentHealthChange / 2) + (card.cardDraw * 2) + getAbilitiesRating(card)
        RED_ITEM, BLUE_ITEM -> (- card.attack - card.defense + (card.myHealthChange / 2) - (card.opponentHealthChange / 2) + (card.cardDraw * 2)).toDouble() // remove abilities is very situational
    }
    return rating - (card.cost * 2 + 1)
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
        rating += 1
    }
    if (card.abilities.contains(WARD)) {
        rating += card.attack
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

class State(val board: Board, private var players: List<Player>, val hand: MutableList<Card>, val deck: MutableList<Card>) {
    fun me(): Player {
        return players[0]
    }

    fun opponent(): Player {
        return players[1]
    }
}

class Board(val myCards: MutableList<Creature>, val opponentCards: MutableList<Creature>) {
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