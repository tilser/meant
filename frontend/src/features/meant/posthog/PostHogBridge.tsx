import { useEffect } from 'react'

import { supabase } from '../../../lib/supabase'
import { installPostHogAnalyticsBridge } from './postHogBridgeLifecycle'
import { startBrowserPostHogAnalytics } from './postHogAnalytics'

export function PostHogAnalytics() {
  useEffect(() => {
    return installPostHogAnalyticsBridge(startBrowserPostHogAnalytics(), supabase.auth)
  }, [])

  return null
}
