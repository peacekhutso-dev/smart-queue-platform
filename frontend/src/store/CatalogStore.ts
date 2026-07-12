import { create } from 'zustand';
import { ApplicantType, ServiceOffering } from '../types';
import { catalogService } from '../api/Api';

interface CatalogState {
  applicantTypes: ApplicantType[];
  selectedApplicantType: string | null;
  selectedService: ServiceOffering | null;
  loaded: boolean;
  loading: boolean;
  loadCatalog: () => Promise<void>;
  setSelectedApplicantType: (code: string) => void;
  setSelectedService: (service: ServiceOffering) => void;
  servicesForSelectedApplicantType: () => ServiceOffering[];
  reset: () => void;
}

export const useCatalogStore = create<CatalogState>((set, get) => ({
  applicantTypes: [],
  selectedApplicantType: null,
  selectedService: null,
  loaded: false,
  loading: false,

  loadCatalog: async () => {
    if (get().loaded || get().loading) return;
    set({ loading: true });
    try {
      const res = await catalogService.getCatalog();
      set({ applicantTypes: res.data.data.applicantTypes, loaded: true });
    } finally {
      set({ loading: false });
    }
  },

  setSelectedApplicantType: (code) => set({ selectedApplicantType: code, selectedService: null }),

  setSelectedService: (service) => set({ selectedService: service }),

  servicesForSelectedApplicantType: () => {
    const { applicantTypes, selectedApplicantType } = get();
    return applicantTypes.find(at => at.code === selectedApplicantType)?.services ?? [];
  },

  reset: () => set({ selectedApplicantType: null, selectedService: null }),
}));
