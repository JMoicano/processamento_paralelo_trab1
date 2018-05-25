#!/bin/sh

if [ $# -ge 4 ] 
then
	ip=$1
	path_to_dict=$2
	crypted_file=$3
	known_word=$4

	if [ $# -eq 6 ]
	then
		known_word+=" $5"
	fi
	java -Djava.rmi.server.hostname=$ip br.inf.ufes.attack.AttackServer $ip $path_to_dict $crypted_file $known_word
else
 	echo "Uso: $0 IP PATH_TO_DICT CRYPTED_FILE KNOWN_WORD"
fi



