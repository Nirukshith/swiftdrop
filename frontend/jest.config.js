module.exports = {
  preset: 'jest-preset-angular',
  setupFilesAfterEnv: ['<rootDir>/setup-jest.ts'],
  testPathIgnorePatterns: ['<rootDir>/node_modules/', '<rootDir>/dist/'],
  modulePathIgnorePatterns: ['<rootDir>/dist/'],
  moduleNameMapper: {
    '^ui$': '<rootDir>/projects/ui/src/public-api.ts',
    '^shared-auth$': '<rootDir>/projects/shared-auth/src/public-api.ts',
  },
  transformIgnorePatterns: ['node_modules/(?!.*\\.mjs$)'],
};
