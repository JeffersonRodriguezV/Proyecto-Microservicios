/**
 * Punto de entrada del API Gateway: solo arranca el servidor.
 * La configuración de rutas y seguridad vive en app.js (así se puede probar sin abrir puertos).
 */
const { app, PORT, destinos } = require('./app');

app.listen(PORT, () => {
    console.log(`API Gateway escuchando en el puerto ${PORT}`);
    console.log(`  /auth/*           -> ${destinos.AUTH_URL}`);
    console.log(`  /empleados/*      -> ${destinos.EMPLEADOS_URL}`);
    console.log(`  /departamentos/*  -> ${destinos.DEPARTAMENTOS_URL}`);
    console.log(`  /perfiles/*       -> ${destinos.PERFILES_URL}`);
    console.log(`  /notificaciones/* -> ${destinos.NOTIFICACIONES_URL}`);
    console.log(`  /vacaciones/*     -> ${destinos.VACACIONES_URL}`);
});
