export interface EventItemRequestDTO {
  itemId: number;
  requestedQuantity: number;
  unitPrice: number;
  rateType: string;
  serials?: string[];
}
