import { parseAppConfig } from './app-config';

describe('parseAppConfig', () => {
  it('acepta una URL y quita las barras finales', () => {
    expect(parseAppConfig({ apiUrl: ' http://localhost:8080/ ' })).toEqual({ apiUrl: 'http://localhost:8080' });
  });

  it('rechaza un config.json sin apiUrl o con apiUrl vacía', () => {
    expect(() => parseAppConfig({})).toThrow(/apiUrl/);
    expect(() => parseAppConfig({ apiUrl: '' })).toThrow(/apiUrl/);
    expect(() => parseAppConfig(null)).toThrow(/apiUrl/);
  });
});
