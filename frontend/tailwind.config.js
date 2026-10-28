const colors = require('tailwindcss/colors');

/** @type {import('tailwindcss').Config} */
module.exports = {
  content: [
    "./projects/**/*.{html,ts}",
  ],
  theme: {
    extend: {
      // SwiftDrop design tokens. Use these names in components instead of raw palette colours
      // so the look can be changed in one place. See projects/ui/README.md.
      colors: {
        brand: colors.indigo,
        success: colors.emerald,
        warning: colors.amber,
        danger: colors.rose,
        info: colors.sky,
        neutral: colors.slate,
        surface: {
          DEFAULT: colors.white,
          muted: colors.slate[50],
          border: colors.slate[200],
        },
        ink: {
          DEFAULT: colors.slate[900],
          muted: colors.slate[600],
          subtle: colors.slate[400],
        },
      },
      borderRadius: {
        control: '0.5rem',
        card: '0.75rem',
      },
      boxShadow: {
        card: '0 1px 2px 0 rgb(15 23 42 / 0.06), 0 1px 3px 0 rgb(15 23 42 / 0.10)',
        overlay: '0 20px 25px -5px rgb(15 23 42 / 0.15), 0 8px 10px -6px rgb(15 23 42 / 0.10)',
      },
      zIndex: {
        overlay: '40',
        toast: '50',
      },
    },
  },
  plugins: [],
};
