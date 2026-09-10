// api/youtube-upload.js
// Rep un vídeo des de l'app, el puja a YouTube com a "no listat"
// i retorna l'ID del vídeo per fer l'embed

const { google } = require('googleapis')
const { Readable } = require('stream')

function getOAuthClient() {
  const oauth2Client = new google.auth.OAuth2(
    process.env.YOUTUBE_CLIENT_ID,
    process.env.YOUTUBE_CLIENT_SECRET,
    process.env.YOUTUBE_REDIRECT_URI
  )
  oauth2Client.setCredentials({
    refresh_token: process.env.YOUTUBE_REFRESH_TOKEN
  })
  return oauth2Client
}

export const config = {
  api: {
    bodyParser: false,
    responseLimit: '100mb',
  },
}

module.exports = async function handler(req, res) {
  // Només acceptem POST
  if (req.method !== 'POST') {
    return res.status(405).json({ error: 'Mètode no permès' })
  }

  try {
    // Llegim el body com a buffer
    const chunks = []
    for await (const chunk of req) {
      chunks.push(chunk)
    }
    const buffer = Buffer.concat(chunks)

    // Obtenim metadades de les capçaleres
    const titol = req.headers['x-video-title'] || 'Carmen 113 - Vídeo'
    const descripcio = req.headers['x-video-description'] || ''
    const concertId = req.headers['x-concert-id'] || ''

    // Autenticació amb YouTube
    const auth = getOAuthClient()
    const youtube = google.youtube({ version: 'v3', auth })

    // Pugem el vídeo
    const response = await youtube.videos.insert({
      part: ['snippet', 'status'],
      requestBody: {
        snippet: {
          title: titol,
          description: `${descripcio}\n\nConcert ID: ${concertId}\nCarmen 113 · Mañanas de Euforia`,
          categoryId: '10', // Música
        },
        status: {
          privacyStatus: 'unlisted', // No listat: accessible amb l'enllaç però no públic
        },
      },
      media: {
        mimeType: req.headers['content-type'] || 'video/mp4',
        body: Readable.from(buffer),
      },
    })

    const videoId = response.data.id

    res.status(200).json({
      ok: true,
      videoId,
      embedUrl: `https://www.youtube.com/embed/${videoId}`,
      watchUrl: `https://www.youtube.com/watch?v=${videoId}`,
    })

  } catch (err) {
    console.error('Error pujant vídeo:', err)
    res.status(500).json({ error: 'Error pujant el vídeo', detail: err.message })
  }
}
