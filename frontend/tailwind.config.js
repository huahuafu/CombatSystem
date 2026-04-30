/** @type {import('tailwindcss').Config} */
export default {
  content: ["./index.html", "./src/**/*.{js,jsx}"],
  theme: {
    extend: {
      colors: {
        commandBg: "#0b1220",
        commandPanel: "#111a2d"
      }
    }
  },
  plugins: []
};
