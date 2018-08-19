#!/usr/bin/env bash

TEMP_DIR=$(mktemp -d -t play-)
mkfifo "$TEMP_DIR/in.file" "$TEMP_DIR/out.file" "$TEMP_DIR/err.file"
# echo "$TEMP_DIR" >> $8
echo "$TEMP_DIR/in.file $TEMP_DIR/out.file $TEMP_DIR/err.file $1 $2 $3 $4 $5 $6" >> $7

cat - $TEMP_DIR/out.file >&1 &
cat - $TEMP_DIR/err.file >/dev/null &
cat - <&0 >$TEMP_DIR/in.file

sleep 3600 # wait 1h
#rm -r "$TEMP_DIR"
