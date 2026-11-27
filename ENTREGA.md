# Entrega — Vacunatorio TP5 Grupo 8

## Estructura

- `backend/` — Java (simulación + API REST)
- `frontend/` — Interfaz web

## Ejecutar

```bash
./compilar.sh    # primera vez
./ejecutar.sh
```

Abrir **http://127.0.0.1:8080**

Windows: `compilar.bat` → `ejecutar.bat`

## Cerrar y volver a abrir

`Ctrl+C` para cerrar. Luego `./ejecutar.sh` otra vez.

## Actualización vector de estado TP5

- El vector de estado se actualizó para seguir el planteo de `PlanteoVectorEstado.ods`.
- El reloj de la simulación discreta se expresa en minutos.
- Por defecto se usan los RND fijos del `.ods` y se muestran solo las 13 filas manuales para controlar la lógica.
- El Runge-Kutta no fue modificado. Su salida de 720 segundos se utiliza como 12 minutos para el vencimiento de las dosis de gripe dentro del vector.
- La interfaz muestra todas las columnas del vector de estado, incluyendo eventos, RND usados, objetos permanentes, estadísticas y objetos temporales activos.
