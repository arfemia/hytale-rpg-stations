'use client'

import { ReactNode } from 'react'
import { useSidebar } from './SidebarContext'

export function MainContent({ children }: { children: ReactNode }) {
  const { collapsed } = useSidebar()

  return (
    <div className={`flex-1 min-w-0 flex flex-col transition-[margin] duration-200 ${collapsed ? '' : 'lg:ml-72'}`}>
      {children}
    </div>
  )
}
