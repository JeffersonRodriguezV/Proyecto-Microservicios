/**
 * Seguridad del Gateway (Reto 5): autenticación JWT + autorización RBAC/propiedad.
 * El Gateway no contiene lógica de negocio: solo decide si la petición pasa o no.
 */
const jwt = require('jsonwebtoken');

const JWT_SECRET = process.env.JWT_SECRET;
if (!JWT_SECRET || JWT_SECRET.length < 32) {
    console.error('JWT_SECRET no definido o menor a 32 caracteres. Defínalo en el .env');
    process.exit(1);
}

const METODOS_LECTURA = ['GET', 'HEAD', 'OPTIONS'];

// Rutas públicas: el cliente aún no tiene token.
const RUTAS_PUBLICAS = [
    { metodo: 'POST', ruta: '/auth/login' },
    { metodo: 'POST', ruta: '/auth/recover-password' },
    { metodo: 'POST', ruta: '/auth/reset-password' },
];

// Documentación Swagger/OpenAPI de cada servicio (solo lectura).
const REGEX_DOCS = /\/(swagger[\w-]*|api-docs|openapi[\w.]*|docs)(\/|\.|$)/i;

function respuesta(res, status, mensaje) {
    res.status(status).json({ status, mensaje, timestamp: new Date().toISOString() });
}

function esPublica(req) {
    if (req.method === 'GET' && REGEX_DOCS.test(req.path)) return true;
    return RUTAS_PUBLICAS.some(r => r.metodo === req.method && r.ruta === req.path);
}

/** 401: falta el token, está alterado, vencido, o no es un token de acceso. */
function autenticar(req, res, next) {
    if (esPublica(req)) return next();

    const cabecera = req.headers.authorization || '';
    if (!cabecera.startsWith('Bearer ')) {
        return respuesta(res, 401, 'Se requiere el encabezado Authorization: Bearer <token>');
    }

    try {
        const claims = jwt.verify(cabecera.substring(7).trim(), JWT_SECRET, { algorithms: ['HS256'] });
        // Mismo criterio que el auth-service: un token de acceso lleva role y NO lleva type.
        // Así un token de activación/recuperación (que viaja por correo) no abre la API.
        if (!claims.role || claims.type) {
            return respuesta(res, 401, 'Token no válido para acceder a este recurso');
        }
        req.usuario = { id: String(claims.sub), rol: claims.role };
        return next();
    } catch (e) {
        return respuesta(res, 401, 'Token inválido o expirado');
    }
}

/**
 * 403: autenticado pero sin permiso.
 *   ADMIN                                   -> todo
 *   USER + lectura                          -> permitido
 *   USER + POST /auth/change-password       -> permitido (el auth-service la ata al sub del token)
 *   USER + PUT /perfiles/{id} con id == sub -> permitido (propiedad del recurso)
 *   cualquier otro caso                     -> 403
 */
function autorizar(req, res, next) {
    if (esPublica(req)) return next();

    const { rol, id } = req.usuario;
    if (rol === 'ADMIN') return next();

    if (rol === 'USER') {
        if (METODOS_LECTURA.includes(req.method)) return next();

        if (req.method === 'POST' && req.path === '/auth/change-password') return next();

        const perfil = req.path.match(/^\/perfiles\/([^/]+)\/?$/);
        if (req.method === 'PUT' && perfil && decodeURIComponent(perfil[1]) === id) return next();
    }

    return respuesta(res, 403, 'No tiene permisos para realizar esta acción');
}

module.exports = { autenticar, autorizar };