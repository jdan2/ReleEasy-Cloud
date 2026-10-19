import { parseAppConfig } from './app-config';

describe('parseAppConfig', () => {
  it('acepta una URL y quita las barras finales', () => {
    expect(parseAppConfig({ apiUrl: ' http://localhost:8080/ ' })).toEqual({
      apiUrl: 'http://localhost:8080',
      mockApi: false,
      githubAppSlug: null,
      githubClientId: null,
    });
  });

  it('lee el slug de la GitHub App si viene informado', () => {
    expect(parseAppConfig({ apiUrl: 'http://x', githubAppSlug: ' releasy-cloud ' }).githubAppSlug).toBe('releasy-cloud');
    expect(parseAppConfig({ apiUrl: 'http://x', githubAppSlug: '' }).githubAppSlug).toBeNull();
    expect(parseAppConfig({ apiUrl: 'http://x', githubClientId: 'Iv23li' }).githubClientId).toBe('Iv23li');
  });

  it('activa el modo demo solo con mockApi: true', () => {
    expect(parseAppConfig({ apiUrl: 'http://x', mockApi: true }).mockApi).toBe(true);
    expect(parseAppConfig({ apiUrl: 'http://x', mockApi: 'true' }).mockApi).toBe(false);
  });

  it('rechaza un config.json sin apiUrl o con apiUrl vacía', () => {
    expect(() => parseAppConfig({})).toThrow(/apiUrl/);
    expect(() => parseAppConfig({ apiUrl: '' })).toThrow(/apiUrl/);
    expect(() => parseAppConfig(null)).toThrow(/apiUrl/);
  });
});
