/*
 * Pregunta 2b - Laboratorio 5 de Algoritmia
 *
 * ESTRATEGIA:
 * Se usa BACKTRACKING para explorar el campo minado desde la celda (0,0)
 * hasta la celda (n-1, m-1). Cada vez que se alcanza el destino se
 * considera una ruta encontrada, se imprime el tablero con las letras
 * que marcan el recorrido y se "desmarca" para seguir buscando otras
 * rutas posibles. La búsqueda se detiene en cuanto se encuentran 20
 * rutas distintas.
 *
 * - No se usan variables globales: todo se pasa por parámetro.
 * - La única función recursiva es la de backtracking (buscarRutas).
 * - Las minas (*) que se "detectan" son únicamente las que están
 *   alrededor de las celdas por las que pasó el robot en cada ruta.
 */

#include <iostream>
#include <vector>
using namespace std;

// Indica si se puede pisar la celda (fila, col):
//  - debe estar dentro del tablero
//  - no debe ser una mina en el campo original
//  - no debe haber sido visitada antes en la ruta actual
bool esCeldaValida(const vector<vector<char>>& campo,
                   const vector<vector<char>>& ruta,
                   int fila, int col, int n, int m) {
    if (fila < 0 || fila >= n) return false;
    if (col  < 0 || col  >= m) return false;
    if (campo[fila][col] == '*') return false;  // es una mina
    if (ruta[fila][col]  != '.') return false;  // ya se pasó por aquí
    return true;
}

// Revisa si una mina en (fila, col) es adyacente (8 vecinos)
// a alguna celda por la que pasó el robot en la ruta actual.
// Sólo en ese caso se considera "detectada" y se imprime.
bool minaDetectada(const vector<vector<char>>& ruta,
                   int fila, int col, int n, int m) {
    for (int di = -1; di <= 1; di++) {
        for (int dj = -1; dj <= 1; dj++) {
            if (di == 0 && dj == 0) continue;
            int nuevaFila = fila + di;
            int nuevaCol  = col  + dj;
            if (nuevaFila >= 0 && nuevaFila < n &&
                nuevaCol  >= 0 && nuevaCol  < m) {
                // si el vecino es una celda visitada de la ruta...
                if (ruta[nuevaFila][nuevaCol] != '.') {
                    return true;
                }
            }
        }
    }
    return false;
}

// Imprime el tablero mostrando:
//   - las letras de la ruta sobre las celdas visitadas
//   - un '*' sólo en las minas que el robot detectó (vecinas a la ruta)
//   - espacios en blanco en el resto de celdas
void imprimirRuta(const vector<vector<char>>& campo,
                  const vector<vector<char>>& ruta, int n, int m) {
    for (int i = 0; i < n; i++) {
        for (int j = 0; j < m; j++) {
            if (ruta[i][j] != '.') {
                // celda por la que pasó el robot
                cout << ruta[i][j] << ' ';
            } else if (campo[i][j] == '*' && minaDetectada(ruta, i, j, n, m)) {
                // mina vecina a la ruta: el sensor la detectó
                cout << "* ";
            } else {
                // mina no detectada o celda libre no visitada
                cout << "  ";
            }
        }
        cout << endl;
    }
}

// ÚNICA función recursiva del programa.
// Explora las 4 direcciones (abajo, derecha, arriba, izquierda)
// marcando cada celda con la letra correspondiente al número de paso.
// Cuando llega al destino, imprime la ruta y aumenta el contador.
// Se detiene en cuanto se encuentran 20 rutas.
void buscarRutas(const vector<vector<char>>& campo,
                 vector<vector<char>>& ruta,
                 int fila, int col, int paso,
                 int n, int m, int& rutasEncontradas) {

    // si ya tenemos las 20 rutas pedidas, dejamos de explorar
    if (rutasEncontradas >= 20) return;

    // si no se puede pisar esta celda, cortamos esta rama
    if (!esCeldaValida(campo, ruta, fila, col, n, m)) return;

    // marcamos la celda con la letra del paso actual
    // (se usa módulo 26 por si la ruta supera las 26 celdas)
    ruta[fila][col] = 'A' + (paso % 26);

    // ¿hemos llegado al destino?
    if (fila == n - 1 && col == m - 1) {
        rutasEncontradas++;
        cout << "Ruta #" << rutasEncontradas << ":" << endl;
        imprimirRuta(campo, ruta, n, m);
        cout << endl;
    } else {
        // El robot se mueve como el rey del ajedrez: 8 direcciones posibles
        // (las 4 ortogonales + las 4 diagonales).
        buscarRutas(campo, ruta, fila + 1, col,     paso + 1, n, m, rutasEncontradas); // abajo
        buscarRutas(campo, ruta, fila + 1, col + 1, paso + 1, n, m, rutasEncontradas); // abajo-derecha
        buscarRutas(campo, ruta, fila,     col + 1, paso + 1, n, m, rutasEncontradas); // derecha
        buscarRutas(campo, ruta, fila - 1, col + 1, paso + 1, n, m, rutasEncontradas); // arriba-derecha
        buscarRutas(campo, ruta, fila - 1, col,     paso + 1, n, m, rutasEncontradas); // arriba
        buscarRutas(campo, ruta, fila - 1, col - 1, paso + 1, n, m, rutasEncontradas); // arriba-izquierda
        buscarRutas(campo, ruta, fila,     col - 1, paso + 1, n, m, rutasEncontradas); // izquierda
        buscarRutas(campo, ruta, fila + 1, col - 1, paso + 1, n, m, rutasEncontradas); // abajo-izquierda
    }

    // desmarcamos la celda para permitir que otras rutas la usen
    ruta[fila][col] = '.';
}

int main() {
    // Dimensiones del campo segun el ejemplo del enunciado: 9 filas x 5 columnas
    int n = 9;
    int m = 5;

    // Campo minado hardcodeado tal como aparece en el ejemplo del PDF.
    // '.' representa una celda libre y '*' representa una mina.
    vector<vector<char>> campo = {
        {'.', '.', '.', '.', '*'},  // fila 0
        {'.', '.', '.', '.', '.'},  // fila 1
        {'.', '.', '*', '.', '.'},  // fila 2
        {'.', '.', '.', '.', '.'},  // fila 3
        {'.', '*', '*', '.', '*'},  // fila 4
        {'.', '*', '*', '.', '*'},  // fila 5
        {'.', '.', '.', '*', '.'},  // fila 6
        {'*', '*', '*', '*', '.'},  // fila 7
        {'.', '.', '.', '.', '.'}   // fila 8
    };

    // tablero auxiliar que va guardando la ruta actual
    // '.' significa no visitada; una letra significa visitada
    vector<vector<char>> ruta(n, vector<char>(m, '.'));

    int rutasEncontradas = 0;
    buscarRutas(campo, ruta, 0, 0, 0, n, m, rutasEncontradas);

    // mensaje final segun el resultado
    if (rutasEncontradas == 0) {
        cout << "No hay solucion posible." << endl;
    } else {
        cout << "Se encontraron " << rutasEncontradas
             << " ruta(s) (maximo 20)." << endl;
    }

    return 0;
}
