#! /usr/bin/env scala

// case class Coefs(
//   val myBoard: Double,
//   val opBoard: Double,
//   val myHealth: Double,
//   val opHealth: Double,
//   val myMana: Double,
//   val myDeck: Double,
//   val opDeck: Double,
//   val myHand: Double
// )

val MAX_ROUNDS = 100
val MAX_NOOP_ROUNDS = 5
val POPULATION = 12
val STEP = .25d

// Return success rate of a against b
def eval(a: Array[Double], b: Array[Double]): Double = {
  val out = sys.process.Process(Seq("java", "-jar", "tester/cg-brutaltester.jar",
    "-r", "java -Dleague.level=4 -Dverbose.level=0 -jar tester/locam-referee.jar",
    "-p1", s"java -jar target/game.jar ${a.mkString(" ")}",
    "-p2", s"java -jar target/game.jar ${b.mkString(" ")}",
    //"-p1", s"tester/play.sh ${a.mkString(" ")} ../command.fifo",
    //"-p2", s"tester/play.sh ${b.mkString(" ")} ../command.fifo",
    "-t", "1", "-n", "5", "-s" //, "-v", "-l", "game/logs"
  )).!!
  val m = SCORE_RE.findFirstMatchIn(out).get
  System.err.println(s"Pitched ${a.mkString(",")} against ${b.mkString(",")} : ${m.group(1)}")
  m.group(1).replace(',', '.').toDouble / 100d
}
val SCORE_RE = """(?m)^\| Player 1 \|\s+\|\s*(\d+[\.,]\d+)%\s*\|$""".r

val INIT_STR = """
	6.50	2.25, -1.75, 1.0, -1.5, -0.75, 1.5
	6.00	2.25, -1.75, 1.25, -1.75, 0.75, 1.0
	5.80	2.25, -1.75, 1.0, -1.5, -0.75, 1.25
	5.80	2.0, -1.75, 1.0, -1.5, -0.75, 1.5
	5.70	2.25, -2.0, 1.5, -2.0, 0.75, 1.75
	5.30	2.25, -1.75, 1.0, -1.5, -0.75, 1.5
	5.30	2.25, -1.75, 1.25, -1.75, 0.75, 1.25
	5.20	2.25, -2.0, 1.5, -2.25, 0.75, 1.75
	5.20	2.25, -1.75, 1.0, -1.75, -0.75, 1.5
	5.10	2.25, -1.75, 1.0, -1.5, -0.75, 1.5
	5.10	2.5, -2.0, 1.75, -2.25, 0.75, 1.75
	5.00	2.5, -2.0, 1.5, -2.25, 0.75, 1.75
"""
val INIT_RE = """^\s*+(?:[\d.]+\s+)?+(\d.++)$""".r
val INIT_COEFS = INIT_STR.split("""\r?\n""").map(_.trim).filter(!_.isEmpty)
  .map({ line =>
    val INIT_RE(coefs) = line
    coefs.split(", ").map(_.toDouble)
  }).toList

val INIT = Array(
   2d, // my board
  -2d, // opponent's board
   1d, // my health
  -1d, // opponent's health
  -1d, // my mana
   1d  // my hand
)
val RANDOM = new scala.util.Random

def procreate(parent: Array[Double]): Array[Double] = {
  val child = parent.clone()
  child(RANDOM.nextInt(child.length)) += STEP * (if (RANDOM.nextBoolean) 1.0d else -1.0d)
  child
}

var population: List[Array[Double]] = INIT_COEFS match {
  case Nil => INIT.clone() +: (1 until POPULATION).map(_ => procreate(INIT)).toList
  case l   => l
}

var rounds = 0
var noOpRounds = 0

while (noOpRounds < MAX_NOOP_ROUNDS && rounds < MAX_ROUNDS) {
  val scores = Array.fill(population.length)(0d)
  for (List((a, ai), (b, bi)) <- population.zipWithIndex.combinations(2).toList.par) {
    var s = eval(a, b)
    scores(ai) += s
    scores(bi) += 1d - s
  }
  // sort by descending scores, score -> individual
  val sorted = scores.toList.zipWithIndex.sortBy(-_._1).map(t => (t._1, population(t._2)))
  // half survives & procreates
  val survivors = sorted.map(_._2).take(population.length / 2)
  if (! survivors.zipWithIndex.exists(t => t._1 != population(t._2))) {
    noOpRounds += 1
  } else {
    noOpRounds = 0
  }
  population = survivors ::: survivors.map(procreate)
  System.out.println("ROUND #" + rounds)
  System.out.println("SCORES \n\t" +
    sorted.map(t => s"${"%.2f".format(t._1)}\t${t._2.mkString(", ")}").mkString("\n\t"))
  System.out.println("BEST: " + survivors.head.mkString(", "))
  rounds += 1
}


