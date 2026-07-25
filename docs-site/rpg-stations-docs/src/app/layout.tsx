import { Footer } from '@/components/Footer'
import { Header } from '@/components/Header'
import { MainContent } from '@/components/MainContent'
import { Providers } from '@/components/Providers'
import { Sidebar } from '@/components/Sidebar'
import type { Metadata } from 'next'
import { Inter } from 'next/font/google'
import './globals.css'

const inter = Inter({ subsets: ['latin'] })

export const metadata: Metadata = {
  title: {
    default: 'RPG Stations - Diegetic Work Stations for Hytale Servers',
    template: '%s | RPG Stations Docs',
  },
  description: 'Place a station, press F, watch your character work. A standalone Hytale server mod for diegetic interactive work stations.',
  authors: [{ name: 'ZiggFreed', url: 'https://wintergreen-solutions.com' }],
  creator: 'Wintergreen Solutions',
  publisher: 'Wintergreen Solutions',
  openGraph: {
    type: 'website',
    locale: 'en_US',
    siteName: 'RPG Stations Docs',
    title: 'RPG Stations - Diegetic Work Stations for Hytale Servers',
    description: 'Place a station, press F, watch your character work.',
  },
  other: {
    'theme-color': '#0ea5e9',
  },
}

export default function RootLayout({
  children,
}: {
  children: React.ReactNode
}) {
  return (
    <html lang="en" className="dark">
      <head>
        <meta name="viewport" content="width=device-width, initial-scale=1, maximum-scale=1, user-scalable=no" />
        <meta name="theme-color" content="#0ea5e9" />
      </head>
      <body className={`${inter.className} antialiased`}>
        <Providers>
          <div className="flex min-h-screen overflow-x-hidden">
            <Sidebar />
            <MainContent>
              <Header />
              <main className="flex-1 min-w-0 px-4 py-6 lg:px-8 ">
                {children}
              </main>
              <Footer />
            </MainContent>
          </div>
        </Providers>
      </body>
    </html>
  )
}
