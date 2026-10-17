import { TEMPLATES, defaultOptions, describeOptions, findTemplate, isVisible, validateOptions } from './template-catalog';

const spring = findTemplate('springboot-api')!;
const angular = findTemplate('angular-static')!;

describe('Catálogo de plantillas', () => {
  it('ofrece Angular, Spring Boot y Angular + Spring Boot', () => {
    expect(TEMPLATES.map((t) => t.id)).toEqual(['angular-static', 'springboot-api', 'angular-springboot']);
  });

  it('las claves de opción no se repiten dentro de una plantilla', () => {
    for (const template of TEMPLATES) {
      const keys = template.optionGroups.flatMap((g) => g.fields.map((f) => f.key));
      expect(new Set(keys).size, template.id).toBe(keys.length);
    }
  });

  it('las opciones por defecto son válidas salvo el email de las alarmas activadas', () => {
    for (const template of TEMPLATES) {
      const options = { ...defaultOptions(template), notification_email: 'ops@acme.es' };
      expect(validateOptions(template, options), template.id).toEqual([]);
    }
  });

  it('exige email si hay alarmas activadas', () => {
    const errors = validateOptions(angular, { ...defaultOptions(angular), front_alarms: true, notification_email: '' });
    expect(errors.join()).toContain('Email para alarmas');
  });

  it('exige el dominio solo si se activa "Dominio propio"', () => {
    const base = { ...defaultOptions(angular), front_alarms: false };
    expect(validateOptions(angular, { ...base, custom_domain: false, domain_name: '' })).toEqual([]);
    expect(validateOptions(angular, { ...base, custom_domain: true, domain_name: '' }).join()).toContain('obligatorio');
    expect(validateOptions(angular, { ...base, custom_domain: true, domain_name: 'No Valido' }).join()).toContain('dominio válido');
    expect(validateOptions(angular, { ...base, custom_domain: true, domain_name: 'www.acme.es' })).toEqual([]);
  });

  it('valida rangos numéricos, mínimo ≤ máximo y nombres de variables', () => {
    const base = { ...defaultOptions(spring), backend_alarms: false };
    expect(validateOptions(spring, { ...base, min_tasks: 0 }).join()).toContain('Contenedores mínimos');
    expect(validateOptions(spring, { ...base, min_tasks: 3, max_tasks: 2 }).join()).toContain('no puede ser menor');
    expect(validateOptions(spring, { ...base, env_vars: { 'mal-nombre': 'x' } }).join()).toContain('mal-nombre');
    expect(validateOptions(spring, { ...base, env_vars: { RELEASY_TOKEN: 'x' } }).join()).toContain('reservado');
  });

  it('muestra los campos dependientes solo cuando se cumple su condición', () => {
    expect(isVisible({ key: 'sqs_queue', equals: true }, { sqs_queue: false })).toBe(false);
    expect(isVisible({ key: 'sqs_queue', equals: true }, { sqs_queue: true })).toBe(true);
    expect(isVisible(undefined, {})).toBe(true);
  });

  it('resume los componentes activados para el detalle de la app', () => {
    const summary = describeOptions(spring, { ...defaultOptions(spring), sqs_queue: true, env_vars: { A: '1', B: '2' } });
    expect(summary).toContain('Cola de mensajes (Amazon SQS)');
    expect(summary).toContain('2 variable(s) de entorno');
    expect(summary.some((s) => s.startsWith('Punto de entrada: Balanceador'))).toBe(true);
  });
});
