#!/usr/bin/env bash

TEMP_DIR=$(mktemp -d -t play-)
mkfifo "$TEMP_DIR/in.file" "$TEMP_DIR/out.file" "$TEMP_DIR/err.file"
echo "$TEMP_DIR/in.file $TEMP_DIR/out.file $TEMP_DIR/err.file $1"
rm -r "$TEMP_DIR"
