/**
 * Validates a South African ID number using the Luhn algorithm.
 * Format: YYMMDD SSSS C A Z (13 digits)
 */
export const validateSaId = (id: string): { valid: boolean; error?: string } => {
  if (!id || id.length !== 13 || !/^\d{13}$/.test(id)) {
    return { valid: false, error: 'ID number must be exactly 13 digits.' }
  }

  // Validate date of birth portion (YYMMDD)
  const year  = parseInt(id.substring(0, 2))
  const month = parseInt(id.substring(2, 4))
  const day   = parseInt(id.substring(4, 6))
  if (month < 1 || month > 12 || day < 1 || day > 31) {
    return { valid: false, error: 'ID number contains an invalid date of birth.' }
  }

  // Luhn algorithm checksum
  let sum = 0
  for (let i = 0; i < 12; i++) {
    let digit = parseInt(id[i])
    if (i % 2 !== 0) {
      digit *= 2
      if (digit > 9) digit -= 9
    }
    sum += digit
  }
  const checkDigit = (10 - (sum % 10)) % 10
  if (checkDigit !== parseInt(id[12])) {
    return { valid: false, error: 'The ID number provided is invalid. Please check the digits and try again.' }
  }

  return { valid: true }
}

/** Extract gender from SA ID: 5000–9999 = Male */
export const getGenderFromSaId = (id: string): 'Male' | 'Female' | null => {
  if (id.length < 10) return null
  const seq = parseInt(id.substring(6, 10))
  return seq >= 5000 ? 'Male' : 'Female'
}

/** Extract date of birth from SA ID */
export const getDobFromSaId = (id: string): string | null => {
  if (id.length < 6) return null
  const yy = id.substring(0, 2)
  const mm = id.substring(2, 4)
  const dd = id.substring(4, 6)
  const year = parseInt(yy) > 30 ? `19${yy}` : `20${yy}`
  return `${dd}/${mm}/${year}`
}

/** Format file size to human readable */
export const formatFileSize = (bytes: number): string => {
  if (bytes < 1024) return `${bytes} B`
  if (bytes < 1024 * 1024) return `${(bytes / 1024).toFixed(1)} KB`
  return `${(bytes / (1024 * 1024)).toFixed(1)} MB`
}

/** Format countdown timer MM:SS */
export const formatCountdown = (seconds: number): string => {
  const m = Math.floor(seconds / 60).toString().padStart(2, '0')
  const s = (seconds % 60).toString().padStart(2, '0')
  return `${m}:${s}`
}

/** Clamp a number between min and max */
export const clamp = (val: number, min: number, max: number) =>
  Math.min(Math.max(val, min), max)