import { BookOpen, FileCheck2, FileSignature, FolderOpen, Headphones, MessagesSquare, Scale, Sheet } from 'lucide-react';

export const workplaceModules=[
  {key:'documents',summaryKey:'documents',label:'Quản lý tài liệu',description:'Thư mục, phiên bản, kiểm tra an toàn và chia sẻ theo phạm vi.',permission:'document.read',icon:FolderOpen},
  {key:'knowledge',summaryKey:'publishedArticles',label:'Kho tri thức',description:'Không gian kiến thức, bài viết nội bộ và xuất bản hướng dẫn.',permission:'knowledge.read',icon:BookOpen},
  {key:'approvals',summaryKey:'pendingApprovals',label:'Trung tâm phê duyệt',description:'Tạo yêu cầu, duyệt tuần tự và trao đổi trong từng hồ sơ.',permission:'approval.read',icon:FileCheck2},
  {key:'forms',summaryKey:null,label:'Biểu mẫu nội bộ',description:'Tạo biểu mẫu JSON, phát hành và tiếp nhận phản hồi.',permission:'form.read',icon:Sheet},
  {key:'signatures',summaryKey:'pendingSignatures',label:'Xác nhận điện tử',description:'Luồng ký nội bộ tuần tự với bằng chứng và lịch sử quyết định.',permission:'signature.read',icon:FileSignature},
  {key:'helpdesk',summaryKey:'openTickets',label:'Hỗ trợ nội bộ',description:'Tiếp nhận yêu cầu IT, hành chính, nhân sự và theo dõi xử lý.',permission:'helpdesk.read',icon:Headphones},
  {key:'communication',summaryKey:'activeChannels',label:'Trao đổi nội bộ',description:'Kênh toàn công ty, phòng ban, dự án và hội thoại tập trung.',permission:'communication.read',icon:MessagesSquare},
  {key:'legal',summaryKey:'expiringLegalRecords',label:'Hồ sơ pháp lý',description:'Hợp đồng, giấy phép, thời hạn và nghĩa vụ cần thực hiện.',permission:'legal.read',icon:Scale},
] as const;
export type WorkplaceModuleKey=typeof workplaceModules[number]['key'];
