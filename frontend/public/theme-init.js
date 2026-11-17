// Apply the saved appearance before React and the page content are rendered.
(() => {
  let theme = 'light'
  try {
    if (localStorage.getItem('flashmind.theme') === 'dark') theme = 'dark'
  } catch { /* The selected theme can still be changed for this tab without storage. */ }
  document.documentElement.dataset.theme = theme
  document.documentElement.style.colorScheme = theme
})()
