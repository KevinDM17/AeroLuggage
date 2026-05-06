#!/bin/bash

# ============================================================
# Configuración — agregar o quitar cadenas de la lista
# ============================================================
CADENAS=(
  "20270117"
)

# ============================================================
# Parámetros
# ============================================================
ARCHIVO_ENTRADA="$1"
CARPETA_SALIDA="$2"

# Verificar que se pasaron los argumentos
if [[ -z "$ARCHIVO_ENTRADA" || -z "$CARPETA_SALIDA" ]]; then
  echo "Uso: $0 <archivo_entrada> <carpeta_salida>"
  exit 1
fi

# Verificar que el archivo de entrada existe
if [[ ! -f "$ARCHIVO_ENTRADA" ]]; then
  echo "Error: no se encontró el archivo '$ARCHIVO_ENTRADA'"
  exit 1
fi

# Crear carpeta de salida si no existe
mkdir -p "$CARPETA_SALIDA"

# Construir patrón combinado: cadena1|cadena2|cadena3
PATRON=$(IFS="|"; echo "${CADENAS[*]}")

# Filtrar — conservar solo líneas que contengan al menos una cadena
# El archivo de salida tiene el mismo nombre que el original
NOMBRE_ARCHIVO=$(basename "$ARCHIVO_ENTRADA")
grep -E "$PATRON" "$ARCHIVO_ENTRADA" > "$CARPETA_SALIDA/$NOMBRE_ARCHIVO"

echo "Cadenas buscadas: ${CADENAS[*]}"
echo "Líneas conservadas: $(wc -l < "$CARPETA_SALIDA/$NOMBRE_ARCHIVO")"
echo "Guardado en: $CARPETA_SALIDA/$NOMBRE_ARCHIVO"