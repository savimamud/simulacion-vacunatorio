# Vacunatorio — TP5 Grupo 8 (Java)

## Estructura

```
tp sim/
├── backend/          API + motor de simulación (Java / Javalin)
├── frontend/         Interfaz web (Vite + JavaScript)
├── compilar.sh       Compila frontend + backend
├── ejecutar.sh       Inicia la aplicación
└── README.md
```

## Ejecutar (cada vez)

```bash
cd simulacion-vacunatorio
./ejecutar.sh
```

Abrí **http://127.0.0.1:8080**

Cerrar con `Ctrl+C`. Para volver a abrir: `./ejecutar.sh` de nuevo.

## Primera vez o tras cambios

```bash
./compilar.sh
./ejecutar.sh
```

**Requisitos:** Java 17+, Node.js 18+ (solo para compilar el frontend)

## Windows

`compilar.bat` → `ejecutar.bat`

## Desarrollo (opcional)

Terminal 1: `./ejecutar.sh` (backend en :8080)  
Terminal 2: `cd frontend && npm run dev` (interfaz en :5173 con proxy a la API)
