import { Pipe, PipeTransform } from '@angular/core';

@Pipe({ name: 'replace' })
export class ReplacePipe implements PipeTransform {
  transform(value: string, from: string, to: string): string {
    if (!value) return value;
    return value.split(from).join(to);
  }
}
