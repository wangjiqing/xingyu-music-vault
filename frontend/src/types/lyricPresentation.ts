export interface LyricPresentationHint {
  displayOnly?: boolean | null
  suggestedStartMs?: number | null
  suggestedEndMs?: number | null
}

export interface PreservedLyricHeaderLine {
  text?: string | null
  kind?: string | null
  lineClassification?: string | null
  sourceLineIndex?: number | null
  participatedInAlignment?: boolean | null
  nonAlignmentReason?: string | null
  presentationHints?: LyricPresentationHint | null
}

export interface LyricAlignmentPresentation {
  firstAlignedLyricStartMs?: number | null
  preservedHeaderLines?: PreservedLyricHeaderLine[] | null
  presentationHints?: LyricPresentationHint[] | null
}
