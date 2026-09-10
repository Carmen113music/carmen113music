// api/youtube-auth.js
// Gestiona el flux OAuth amb Google/YouTube
// Primera vegada: redirigeix a Google per fer login
// Després del login: Google redirigeix aquí amb un codi, l'intercanviem per tokens

const { google } = require('googleapis')

function getOAuthClient() {
  return new google.auth.OAuth2(
    process.env.YOUTUBE_CLIENT_ID,
    process.env.YOUTUBE_CLIENT_SECRET,
    process.env.YOUTUBE_REDIRECT_URI
  )
}

module.exports = async function handler(req, res) {
  const oauth2Client = getOAuthClient()

  // Si no hi ha codi, redirigim a Google per fer login
  if (!req.query.code) {
    const url = oauth2Client.generateAuthUrl({
      access_type: 'offline',
      scope: ['https://www.googleapis.com/auth/youtube.upload'],
      prompt: 'consent'
    })
    return res.redirect(url)
  }

  // Google ens ha retornat un codi, l'intercanviem per tokens
  try {
    const { tokens } = await oauth2Client.getToken(req.query.code)

    // Mostrem el refresh_token per copiar-lo a les variables d'entorn
    // Això només cal fer-ho UNA vegada
    res.status(200).send(`
      <html>
        <body style="font-family:monospace;padding:40px;background:#111;color:#fff">
          <h2 style="color:#FF66FF">✅ YouTube connectat!</h2>
          <p>Copia aquest <strong>refresh_token</strong> i afegeix-lo a Vercel com a variable d'entorn <code>YOUTUBE_REFRESH_TOKEN</code>:</p>
          <textarea style="width:100%;height:120px;background:#222;color:#FF66FF;border:1px solid #FF66FF;padding:12px;font-size:13px;margin-top:16px">${tokens.refresh_token}</textarea>
          <p style="margin-top:24px;color:#888">Un cop afegit a Vercel, aquesta pàgina ja no cal.</p>
        </body>
      </html>
    `)
  } catch (err) {
    res.status(500).json({ error: 'Error obtenint tokens', detail: err.message })
  }
}
