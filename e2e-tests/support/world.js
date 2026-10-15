const { World, setWorldConstructor, setDefaultTimeout } = require('@cucumber/cucumber');
const config = require('./config');

/**
 * Contexto compartido entre pasos ("World").
 *
 * Decisiones de diseno:
 *  - Cucumber crea UNA INSTANCIA NUEVA por escenario, asi que el token y la ultima respuesta
 *    nunca se filtran de un escenario a otro (aislamiento).
 *  - El token se guarda en el World (this.token) y se envia automaticamente en cada solicitud.
 *    Un paso "Dado" lo obtiene y los pasos "Cuando" lo reutilizan sin repetir el login.
 *  - La autenticacion va en los Antecedentes ("Dado que estoy autenticado como ADMIN"),
 *    una vez por escenario, en lugar de repetirse en cada escenario.
 *  - BASE_URL y las credenciales vienen de variables de entorno (ver support/config.js).
 */
class MundoApi extends World {
  constructor(options) {
    super(options);
    this.baseUrl = config.baseUrl;
    this.token = null;
    this.ultimaRespuesta = null;
  }

  /**
   * Envia una solicitud HTTP al sistema y guarda la respuesta en this.ultimaRespuesta.
   * @param {string} metodo  GET, POST, PUT, DELETE...
   * @param {string} ruta    por ejemplo '/empleados'
   * @param {object} [opciones]
   * @param {*}      [opciones.body]   se envia como JSON
   * @param {string|null} [opciones.token]  por defecto el token del escenario; null = sin token
   * @returns {Promise<{status:number, cuerpo:*, headers:Headers}>}
   */
  async solicitar(metodo, ruta, { body, token = this.token } = {}) {
    const headers = {};
    if (body !== undefined) headers['Content-Type'] = 'application/json';
    if (token) headers['Authorization'] = `Bearer ${token}`;

    const respuesta = await fetch(`${this.baseUrl}${ruta}`, {
      method: metodo,
      headers,
      body: body !== undefined ? JSON.stringify(body) : undefined
    });

    const texto = await respuesta.text();
    let cuerpo = null;
    try {
      cuerpo = texto ? JSON.parse(texto) : null;
    } catch {
      cuerpo = texto; // la respuesta no era JSON
    }

    this.ultimaRespuesta = { status: respuesta.status, cuerpo, headers: respuesta.headers };
    return this.ultimaRespuesta;
  }

  /** Inicia sesion con las credenciales del rol (ADMIN o USER) y guarda el token en el World. */
  async autenticarComo(rol) {
    const credenciales = config.usuarios[rol];
    if (!credenciales) {
      throw new Error(`Rol desconocido: '${rol}'. Use ADMIN o USER.`);
    }
    const respuesta = await this.solicitar('POST', '/auth/login', { body: credenciales, token: null });
    if (respuesta.status !== 200) {
      throw new Error(`El login de ${rol} fallo: HTTP ${respuesta.status} ${JSON.stringify(respuesta.cuerpo)}`);
    }
    this.token = respuesta.cuerpo.accessToken;
    return this.token;
  }
}

setWorldConstructor(MundoApi);
setDefaultTimeout(60 * 1000);
