/**
 * Local answer check: normalisation, alternatives, optional parts and typo
 * tolerance. Cases this cannot decide are "unclear" and go to the AI (or,
 * without AI, count as wrong with an "I was right" option).
 */

export type LocalVerdict = 'exact' | 'typo' | 'empty' | 'unclear';

/** Separators between alternative meanings in a solution. */
const SEPARATORS = /[,;/]/;

/** Case, whitespace, punctuation at the edges and Unicode form do not matter. */
export function normalize(text: string): string {
  return text
    .normalize('NFC')
    .toLowerCase()
    .replace(/\s+/g, ' ')
    .replace(/^[\s\p{P}]+|[\s\p{P}]+$/gu, '');
}

/**
 * Accepted spellings of one alternative: with the parts in brackets and
 * without them. "groß (bedeutend)" → ["groß bedeutend", "groß"].
 */
function variants(alternative: string): string[] {
  const withParts = alternative.replace(/[()[\]]/g, '');
  const withoutParts = alternative.replace(/\([^)]*\)|\[[^\]]*\]/g, ' ');
  return [...new Set([withParts, withoutParts].map(normalize).filter(Boolean))];
}

/** All accepted answers for a solution such as "Haus; Gebäude (groß)". */
export function acceptedAnswers(solution: string): string[] {
  // Separators inside brackets belong to the optional part, not to a new alternative.
  const alternatives: string[] = [];
  let depth = 0;
  let current = '';
  for (const ch of solution) {
    if (ch === '(' || ch === '[') depth++;
    if (ch === ')' || ch === ']') depth = Math.max(0, depth - 1);
    if (depth === 0 && SEPARATORS.test(ch)) {
      alternatives.push(current);
      current = '';
    } else {
      current += ch;
    }
  }
  alternatives.push(current);
  return [...new Set(alternatives.flatMap(variants))];
}

/** Optimal string alignment distance (Damerau-Levenshtein with adjacent swaps). */
export function damerauLevenshtein(a: string, b: string): number {
  const s = [...a];
  const t = [...b];
  const d: number[][] = Array.from({ length: s.length + 1 }, (_, i) =>
    Array.from({ length: t.length + 1 }, (_, j) => (i === 0 ? j : j === 0 ? i : 0)),
  );
  for (let i = 1; i <= s.length; i++) {
    for (let j = 1; j <= t.length; j++) {
      const cost = s[i - 1] === t[j - 1] ? 0 : 1;
      let best = Math.min(d[i - 1]![j]! + 1, d[i]![j - 1]! + 1, d[i - 1]![j - 1]! + cost);
      if (i > 1 && j > 1 && s[i - 1] === t[j - 2] && s[i - 2] === t[j - 1]) {
        best = Math.min(best, d[i - 2]![j - 2]! + 1);
      }
      d[i]![j] = best;
    }
  }
  return d[s.length]![t.length]!;
}

/**
 * Typos tolerated for a solution of the given length (in characters).
 * Short words must match exactly, otherwise "Bär" would pass as "Bar".
 */
export function allowedTypos(length: number): number {
  if (length <= 4) return 0;
  if (length <= 8) return 1;
  return 2;
}

export interface LocalCheckResult {
  verdict: LocalVerdict;
  /** The accepted spelling the answer matched (for exact/typo). */
  matched?: string;
}

function checkPart(answer: string, accepted: string[]): LocalCheckResult {
  if (accepted.includes(answer)) return { verdict: 'exact', matched: answer };
  let best: { distance: number; matched: string } | null = null;
  for (const candidate of accepted) {
    const distance = damerauLevenshtein(answer, candidate);
    if (distance <= allowedTypos([...candidate].length) && (!best || distance < best.distance)) {
      best = { distance, matched: candidate };
    }
  }
  return best ? { verdict: 'typo', matched: best.matched } : { verdict: 'unclear' };
}

/**
 * Checks a typed answer against a solution. Several meanings typed at once
 * ("Haus, Gebäude") are accepted when each of them is correct.
 */
export function checkLocally(answer: string, solution: string): LocalCheckResult {
  const normalized = normalize(answer);
  if (normalized === '') return { verdict: 'empty' };
  const accepted = acceptedAnswers(solution);

  const whole = checkPart(normalized, accepted);
  if (whole.verdict !== 'unclear') return whole;

  const parts = normalized.split(SEPARATORS).map(normalize).filter(Boolean);
  if (parts.length > 1) {
    const results = parts.map((part) => checkPart(part, accepted));
    if (results.every((r) => r.verdict !== 'unclear')) {
      return { verdict: results.some((r) => r.verdict === 'typo') ? 'typo' : 'exact' };
    }
  }
  return { verdict: 'unclear' };
}
