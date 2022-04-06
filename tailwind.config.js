const colors = require('tailwindcss/colors')
const defaultTheme = require('tailwindcss/defaultTheme')

module.exports = {
  content: [
      "./app/views/Publisher/**/*.scala.html",
      "./app/views/RestAPI/**/*.scala.html",
      "./app/views/tags/publisher/**.scala.html",
      "./public/js/*.js"
  ],
  theme: {
    fontFamily: {
      sans: ['Roboto', ...defaultTheme.fontFamily.sans]
    },
    extend: {},
  },
  plugins:  [
    require('@tailwindcss/forms'),
    require('@tailwindcss/typography'),
    require('@tailwindcss/aspect-ratio')
  ],
}
