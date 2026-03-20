export interface MetaInterestOption {
  id: string
  name: string
  audience_size?: number
  display?: string
}

export interface MetaLocationOption {
  key: string
  name: string
  type: string
  country_name?: string
  country_code?: string
  display?: string
}

export interface MetaAudienceOption {
  id: string
  name: string
  approximate_count?: number
  display?: string
}
