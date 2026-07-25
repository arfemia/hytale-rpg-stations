/** @type {import('next').NextConfig} */
const nextConfig = {
  // Static export deliberately diverges from the MMO docs site's serverless
  // config: this site has no commerce/API-route surface to serve, so it ships
  // as plain static HTML with no deploy wiring owned here.
  output: 'export',
  images: {
    unoptimized: true,
  },
  trailingSlash: true,
  skipTrailingSlashRedirect: true,
}

module.exports = nextConfig
