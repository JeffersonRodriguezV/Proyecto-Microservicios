// Configuracion de Cucumber.js. Las opciones de reportes (JUnit, JSON, HTML) se agregan aqui mas adelante.
module.exports = {
  default: {
    paths: ['features/**/*.feature'],
    require: ['support/**/*.js', 'step_definitions/**/*.js'],
    format: ['progress-bar']
  }
};
