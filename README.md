java -jar ./tester/cg-brutaltester.jar -r "java -jar -Dleague.level=4 -Dverbose.level=0 ./tester/locam-referee.jar" -p1 "java -jar ./target/locam-1.0.0-SNAPSHOT-jar-with-dependencies.jar" -p2 "java -jar ./target/locam-1.0.0-SNAPSHOT-jar-with-dependencies.jar" -t 1 -n 1 -v -l logs/


java -jar ./coding-game/tester/cg-brutaltester.jar -r "java -jar -Dleague.level=4 -Dverbose.level=0 ./coding-game/tester/locam-referee.jar" -p1 "java -jar ./locam-master.jar" -p2 "java -jar ./locamRatingMyBoard.jar" -t 2 -n 1000 -v -l ./coding-game/logs/ -s
