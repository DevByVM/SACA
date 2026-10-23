// Los valores de la paleta se definen como variables en src/index.css
const token = (nombre) => `rgb(var(--saca-${nombre}) / <alpha-value>)`;

export default {
  content: [
    "./index.html",
    "./src/**/*.{js,ts,jsx,tsx}",
  ],
  theme: {
    extend: {
      colors: {
        primaryRed: "#960000",
         primarywed: "#fd6e6e",
        darkRed: "#430000",
        lightGray: "#eeeeee",
        brand: {
          50: token("brand-50"),
          100: token("brand-100"),
          200: token("brand-200"),
          700: token("brand-700"),
          800: token("brand-800"),
          900: token("brand-900"),
        },
        ink: {
          500: token("ink-500"),
          600: token("ink-600"),
          900: token("ink-900"),
        },
        line: token("line"),
        canvas: token("canvas"),
        surface: {
          DEFAULT: token("surface"),
          muted: token("surface-muted"),
        },
        info: {
          50: token("info-50"),
          200: token("info-200"),
          700: token("info-700"),
        },
        success: {
          50: token("success-50"),
          200: token("success-200"),
          700: token("success-700"),
        },
        warning: {
          50: token("warning-50"),
          200: token("warning-200"),
          700: token("warning-700"),
        },
        danger: {
          50: token("danger-50"),
          200: token("danger-200"),
          700: token("danger-700"),
        },
      },
    },
  },
  plugins: [],
}