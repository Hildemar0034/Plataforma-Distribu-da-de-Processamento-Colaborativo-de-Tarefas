#!/bin/bash
# Script de compilação e exemplo de execução (Linux / WSL / Mac)
set -e
rm -rf bin
mkdir -p bin
echo "Compiling..."
find src -name "*.java" > sources.txt
javac -d bin @sources.txt
echo "Compiled. To run:"
echo "java -cp bin orquestrador.OrquestradorPrincipal"
echo "java -cp bin orquestrador.OrquestradorBackup"
echo "java -cp bin worker.Worker worker1"
echo "java -cp bin cliente.Cliente"
