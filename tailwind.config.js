const colors = require('tailwindcss/colors')
const defaultTheme = require('tailwindcss/defaultTheme')

module.exports = {
  content: [
      "./app/views/Publisher/**/*.scala.html",
      "./app/views/RestAPI/**/*.scala.html",
      "./app/views/tags/publisher/**.scala.html",
      "./app/views/Application/index.scala.html",
      "./public/js/*.js"
  ],
  theme: {
      extend: {
          colors: {
              'dvx-white': '#E9E2DB',
              'dvx-mainback': '#5a3e3e',
              'dvx-back': '#312525',
              'dvx-orange': '#E2A86E',
              'dvx-accent': '#F88224',
              'dvx-border': '#5C3A3B',
              'dvx-box': '#121212'
          },
      },
    fontFamily: {
      sans: ['Open Sans', ...defaultTheme.fontFamily.sans],
      serif: ['Aldrich', 'serif'],
        mono: ['Big Shoulders Inline Display', ...defaultTheme.fontFamily.mono],
    },
  },
  plugins:  [
    require('@tailwindcss/forms'),
    require('@tailwindcss/typography'),
    require('@tailwindcss/aspect-ratio')
  ],
}
